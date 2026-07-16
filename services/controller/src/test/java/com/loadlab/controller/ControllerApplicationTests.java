package com.loadlab.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

// The context now holds a @KafkaListener, so without an embedded broker this test
// would sit retrying a connection to localhost:9092.
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {"test-commands", "test-metrics"},
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class ControllerApplicationTests extends AbstractPostgresIntegrationTest {

  @Test
  void contextLoads() {}
}
