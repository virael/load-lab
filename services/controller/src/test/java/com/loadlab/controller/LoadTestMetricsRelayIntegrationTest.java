package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"test-commands", "test-metrics"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class LoadTestMetricsRelayIntegrationTest {

  @Autowired private KafkaTemplate<String, TestResult> kafkaTemplate;

  @Autowired private LoadTestService loadTestService;

  @Autowired private KafkaListenerEndpointRegistry registry;

  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

  // No worker runs in this test; stub the pre-flight so startTest gets to publish.
  @MockitoBean private WorkerClient workerClient;

  @BeforeEach
  void setUp() {
    given(workerClient.isHealthy()).willReturn(true);

    // The controller consumes with auto-offset-reset=latest, so anything published
    // before the listener owns its partition is missed for good. Block until the
    // assignment lands, or this test is a coin flip.
    for (MessageListenerContainer container : registry.getListenerContainers()) {
      ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }
  }

  private static byte[] encode(long... values) {
    Histogram h = new Histogram(1, 60_000, 2);
    for (long v : values) h.recordValue(v);
    ByteBuffer buffer = ByteBuffer.allocate(h.getNeededByteBufferCapacity());
    int len = h.encodeIntoCompressedByteBuffer(buffer);
    byte[] bytes = new byte[len];
    buffer.rewind();
    buffer.get(bytes, 0, len);
    return bytes;
  }

  private static long[] repeat(long value, int count) {
    long[] values = new long[count];
    Arrays.fill(values, value);
    return values;
  }

  @Test
  void computesTruePercentilesByMergingWorkerHistograms() {
    // 9 VU over 3 workers -> sub-runs testId-0, testId-1, testId-2.
    TestResult started = loadTestService.startTest(new TestRequest("http://example.invalid", 9, 1));
    String testId = started.id();
    assertThat(started.status()).isEqualTo("PENDING");

    // Two fast workers, one carrying a slow tail. No single worker's own p99 sees
    // the tail the way the merged distribution does.
    byte[] fast = encode(repeat(10, 100));
    long[] tailValues = repeat(10, 100);
    Arrays.fill(tailValues, 95, 100, 900L);
    byte[] slow = encode(tailValues);

    kafkaTemplate.send(
        "test-metrics",
        testId + "-0",
        new TestResult(testId + "-0", "DONE", 100, 10.0, 0, 10, 10, 10, fast));
    kafkaTemplate.send(
        "test-metrics",
        testId + "-1",
        new TestResult(testId + "-1", "DONE", 100, 10.0, 1, 10, 10, 10, fast));
    kafkaTemplate.send(
        "test-metrics",
        testId + "-2",
        new TestResult(testId + "-2", "DONE", 100, 54.5, 2, 10, 900, 900, slow));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              TestResult merged = loadTestService.getResult(testId);
              assertThat(merged).isNotNull();
              assertThat(merged.status()).isEqualTo("DONE");
              assertThat(merged.totalRequests()).isEqualTo(300); // summed
              assertThat(merged.errors()).isEqualTo(3); // summed
              // Computed from the merged histogram, not from the workers' numbers.
              assertThat(merged.p50Ms()).isBetween(9L, 11L);
              assertThat(merged.p99Ms()).isGreaterThanOrEqualTo(900L);
              // The raw distribution is not forwarded to SSE subscribers.
              assertThat(merged.histogram()).isNull();
            });
  }

  @Test
  void reportsRunningUntilEverySubRunIsDone() {
    TestResult started = loadTestService.startTest(new TestRequest("http://example.invalid", 9, 1));
    String testId = started.id();

    String subId = testId + "-0";
    kafkaTemplate.send(
        "test-metrics",
        subId,
        new TestResult(subId, "DONE", 10, 20.0, 0, 5, 8, 30, encode(repeat(20, 10))));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              TestResult merged = loadTestService.getResult(testId);
              assertThat(merged.totalRequests()).isEqualTo(10);
              // Not done: two sub-runs are still outstanding.
              assertThat(merged.status()).isEqualTo("RUNNING");
            });
  }

  @Test
  void staleSnapshotDoesNotResurrectFinishedTest() {
    TestResult started = loadTestService.startTest(new TestRequest("http://example.invalid", 9, 1));
    String testId = started.id();

    byte[] hist = encode(repeat(10, 100));
    for (int i = 0; i < 3; i++) {
      String subId = testId + "-" + i;
      kafkaTemplate.send(
          "test-metrics", subId, new TestResult(subId, "DONE", 10, 10.0, 0, 10, 10, 10, hist));
    }
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> "DONE".equals(loadTestService.getResult(testId).status()));

    // A worker's 1s scheduler can lose the race with its own DONE message.
    String lateSubId = testId + "-1";
    kafkaTemplate.send(
        "test-metrics",
        lateSubId,
        new TestResult(lateSubId, "RUNNING", 7, 10.0, 0, 10, 10, 10, hist));

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              TestResult merged = loadTestService.getResult(testId);
              assertThat(merged.status()).isEqualTo("DONE");
              assertThat(merged.totalRequests()).isEqualTo(30);
            });
  }
}
