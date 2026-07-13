package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

// Real Kafka broker (Testcontainers), simulated workers. Verifies the controller's
// aggregation end to end: real wire format, real listener, real HistogramMerger —
// without building and booting three worker JVMs in CI.
//
// The broker image matches the one docker-compose already runs, so the test and the
// deployed stack cannot drift apart on broker version.
@SpringBootTest
@Testcontainers
class MultiWorkerAggregationIntegrationTest {

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"));

  private static HttpServer stubTarget;

  @BeforeAll
  static void startStubTarget() throws IOException {
    // Must exist before @DynamicPropertySource reads its port: the Spring context
    // (and WorkerClient with it) is built from those properties. A server created
    // inside @Test would be far too late.
    stubTarget = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    stubTarget.createContext(
        "/ping",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    // No real worker runs here, so this stub answers WorkerClient.isHealthy()'s
    // pre-flight check, which startTest() would otherwise fail on.
    stubTarget.createContext(
        "/actuator/health",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    stubTarget.start();
  }

  @AfterAll
  static void stopStubTarget() {
    stubTarget.stop(0);
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    registry.add("worker.base-url", () -> "http://localhost:" + stubTarget.getAddress().getPort());
  }

  @Autowired private KafkaTemplate<String, TestResult> kafkaTemplate;

  @Autowired private LoadTestService loadTestService;

  @Autowired private KafkaListenerEndpointRegistry registry;

  @BeforeEach
  void waitForListenerAssignment() {
    // The controller consumes with auto-offset-reset=latest, so anything published
    // before the listener owns its partition is lost for good. Block until the
    // assignment lands, or this test is a coin flip.
    for (MessageListenerContainer container : registry.getListenerContainers()) {
      ContainerTestUtils.waitForAssignment(container, 1);
    }
  }

  @Test
  void mergesPercentilesFromMultipleSimulatedWorkers() {
    String targetUrl = "http://localhost:" + stubTarget.getAddress().getPort() + "/ping";
    String testId = loadTestService.startTest(new TestRequest(targetUrl, 10, 1)).id();

    // Mirrors the subId scheme LoadTestService generates for WORKERS_PER_TEST = 3.
    publishSubResult(testId + "-0", fastValues());
    publishSubResult(testId + "-1", fastValues());
    publishSubResult(testId + "-2", slowTailValues());

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              TestResult merged = loadTestService.getResult(testId);
              assertThat(merged).isNotNull();
              assertThat(merged.status()).isEqualTo("DONE");
              assertThat(merged.totalRequests()).isEqualTo(300);
              // Neither fast worker's own p99 shows this tail (each is ~10ms). Only
              // the merged, raw-histogram aggregation does — the same lesson as
              // HistogramMergerTest, now proven across a real Kafka wire.
              assertThat(merged.p99Ms()).isGreaterThanOrEqualTo(400);
              assertThat(merged.p50Ms()).isBetween(9L, 11L);
            });
  }

  private void publishSubResult(String subId, long[] latencies) {
    Histogram histogram = new Histogram(1, 60_000, 2);
    for (long v : latencies) histogram.recordValue(v);
    ByteBuffer buffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
    int len = histogram.encodeIntoCompressedByteBuffer(buffer);
    byte[] bytes = new byte[len];
    buffer.rewind();
    buffer.get(bytes, 0, len);

    var result = new TestResult(subId, "DONE", latencies.length, 0.0, 0, 0, 0, 0, bytes);
    kafkaTemplate.send("test-metrics", subId, result);
  }

  private long[] fastValues() {
    long[] values = new long[100];
    Arrays.fill(values, 10);
    return values;
  }

  private long[] slowTailValues() {
    long[] values = new long[100];
    Arrays.fill(values, 0, 95, 10);
    Arrays.fill(values, 95, 100, 500);
    return values;
  }
}
