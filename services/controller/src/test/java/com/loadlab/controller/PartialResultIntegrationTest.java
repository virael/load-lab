package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
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

// Scope, stated honestly: this proves what the aggregation DOES with a permanently
// failed slice, not that the watchdog's clock fires at the right moment. Testing the
// timing itself would need either a fake clock or a real 15+ second wait; neither is
// worth it here, so the timing is verified by hand against a killed container.
@SpringBootTest
@Testcontainers
class PartialResultIntegrationTest extends AbstractPostgresIntegrationTest {

  @Container
  static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"));

  private static HttpServer stubTarget;

  @BeforeAll
  static void startStubTarget() throws IOException {
    stubTarget = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    stubTarget.createContext(
        "/ping",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
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
    for (MessageListenerContainer container : registry.getListenerContainers()) {
      ContainerTestUtils.waitForAssignment(container, 1);
    }
  }

  @Test
  void marksResultAsPartialWhenOneSubRunPermanentlyFails() {
    String targetUrl = "http://localhost:" + stubTarget.getAddress().getPort() + "/ping";
    String testId = loadTestService.startTest(new TestRequest(targetUrl, 10, 1)).id();

    publish(new TestResult(testId + "-0", "DONE", 100, 10.0, 0, 8, 9, 10, encodeHistogram(10)));
    publish(new TestResult(testId + "-1", "DONE", 100, 10.0, 0, 8, 9, 10, encodeHistogram(10)));
    // Stands in for exactly what the watchdog writes once it runs out of retries:
    // the effect is under test here, not the passage of time that produces it.
    publish(new TestResult(testId + "-2", "FAILED", 0, 0.0, 0, 0, 0, 0, null));

    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              TestResult merged = loadTestService.getResult(testId);
              assertThat(merged).isNotNull();
              assertThat(merged.status()).isEqualTo("PARTIAL");
              // Only the two slices that really ran contribute; the failed one is not
              // silently counted as a zero-request success.
              assertThat(merged.totalRequests()).isEqualTo(200);
            });
  }

  private void publish(TestResult result) {
    kafkaTemplate.send("test-metrics", result.id(), result);
  }

  private byte[] encodeHistogram(long value) {
    var histogram = new Histogram(1, 60_000, 2);
    histogram.recordValue(value);
    var buffer = ByteBuffer.allocate(histogram.getNeededByteBufferCapacity());
    int len = histogram.encodeIntoCompressedByteBuffer(buffer);
    byte[] bytes = new byte[len];
    buffer.rewind();
    buffer.get(bytes, 0, len);
    return bytes;
  }
}
