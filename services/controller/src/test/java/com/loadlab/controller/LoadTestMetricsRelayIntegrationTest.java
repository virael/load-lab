package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;

import java.time.Duration;
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

  @Test
  void mergesSubResultsFromEveryWorkerIntoOneTestResult() {
    // 9 VU over 3 workers -> sub-runs testId-0, testId-1, testId-2.
    TestResult started = loadTestService.startTest(new TestRequest("http://example.invalid", 9, 1));
    String testId = started.id();
    assertThat(started.status()).isEqualTo("PENDING");

    // Stand in for what three real workers would publish.
    for (int i = 0; i < 3; i++) {
      String subId = testId + "-" + i;
      kafkaTemplate.send(
          "test-metrics", subId, new TestResult(subId, "DONE", 10, 20.0, 1, 5, 8, 30 + i));
    }

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              TestResult merged = loadTestService.getResult(testId);
              assertThat(merged).isNotNull();
              assertThat(merged.status()).isEqualTo("DONE");
              assertThat(merged.totalRequests()).isEqualTo(30); // 3 x 10, summed
              assertThat(merged.errors()).isEqualTo(3); // 3 x 1, summed
              assertThat(merged.p99Ms()).isEqualTo(32); // max-of-workers, deliberately naive
            });
  }

  @Test
  void reportsRunningUntilEverySubRunIsDone() {
    TestResult started = loadTestService.startTest(new TestRequest("http://example.invalid", 9, 1));
    String testId = started.id();

    // Only one of the three sub-runs reports in.
    String subId = testId + "-0";
    kafkaTemplate.send("test-metrics", subId, new TestResult(subId, "DONE", 10, 20.0, 0, 5, 8, 30));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              TestResult merged = loadTestService.getResult(testId);
              assertThat(merged.totalRequests()).isEqualTo(10);
              // The test is NOT done: two sub-runs are still outstanding.
              assertThat(merged.status()).isEqualTo("RUNNING");
            });
  }

  @Test
  void staleSnapshotDoesNotResurrectFinishedTest() {
    TestResult started = loadTestService.startTest(new TestRequest("http://example.invalid", 9, 1));
    String testId = started.id();

    for (int i = 0; i < 3; i++) {
      String subId = testId + "-" + i;
      kafkaTemplate.send(
          "test-metrics", subId, new TestResult(subId, "DONE", 10, 20.0, 0, 5, 8, 30));
    }
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> "DONE".equals(loadTestService.getResult(testId).status()));

    // A worker's 1s scheduler can lose the race with its own DONE message.
    String lateSubId = testId + "-1";
    kafkaTemplate.send(
        "test-metrics", lateSubId, new TestResult(lateSubId, "RUNNING", 7, 20.0, 0, 5, 8, 30));

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
