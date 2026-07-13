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
    // Keyed by testId so all messages for the same test land on the same
    // partition, preserving order per test once multiple workers exist (E4.2+).
    kafkaTemplate.send(TOPIC, command.testId(), command);
  }
}
