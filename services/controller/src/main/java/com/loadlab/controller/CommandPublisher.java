package com.loadlab.controller;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CommandPublisher {

  public static final String TOPIC = "test-commands";

  private final KafkaTemplate<String, RunCommand> kafkaTemplate;

  public CommandPublisher(KafkaTemplate<String, RunCommand> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void publish(RunCommand command) {
    // Keyed by subId, so distinct slices of one test can hash to different
    // partitions and therefore reach different workers in the group.
    kafkaTemplate.send(TOPIC, command.subId(), command);
  }
}
