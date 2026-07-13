package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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

  @BeforeEach
  void waitForListenerAssignment() {
    // The controller consumes with auto-offset-reset=latest, so anything published
    // before the listener owns its partition is missed for good. Block until the
    // assignment lands, or this test is a coin flip.
    for (MessageListenerContainer container : registry.getListenerContainers()) {
      ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }
  }

  @Test
  void relaysMetricsFromKafkaIntoStore() {
    // Stands in for what a real worker would publish — no worker process needed.
    var result = new TestResult("relay-test-1", "DONE", 42, 12.5, 0, 10, 15, 19);
    kafkaTemplate.send("test-metrics", result.id(), result);

    // Consumption is asynchronous, so poll until the listener catches up.
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              TestResult stored = loadTestService.getResult("relay-test-1");
              assertThat(stored).isNotNull();
              assertThat(stored.status()).isEqualTo("DONE");
              assertThat(stored.totalRequests()).isEqualTo(42);
            });
  }

  @Test
  void staleRunningSnapshotDoesNotResurrectFinishedTest() {
    var done = new TestResult("relay-test-2", "DONE", 100, 10.0, 0, 9, 12, 15);
    kafkaTemplate.send("test-metrics", done.id(), done);
    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> loadTestService.getResult("relay-test-2") != null);

    // The worker's 1s scheduler can lose the race with its own DONE message.
    var lateRunning = new TestResult("relay-test-2", "RUNNING", 90, 10.0, 0, 9, 12, 15);
    kafkaTemplate.send("test-metrics", lateRunning.id(), lateRunning);

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> assertThat(loadTestService.getResult("relay-test-2").status()).isEqualTo("DONE"));
  }
}
