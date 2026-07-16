package com.loadlab.controller;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

// Publishes the MERGED, front-facing result onto a second topic so the aggregator
// consumes a single source of truth (keyed by testId) instead of re-implementing the
// cross-worker histogram merge on the raw test-metrics stream.
@Component
public class ResultPublisher {

  public static final String TOPIC = "test-results";

  private final KafkaTemplate<String, TestResult> kafkaTemplate;

  public ResultPublisher(KafkaTemplate<String, TestResult> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void publish(TestResult result) {
    kafkaTemplate.send(TOPIC, result.id(), result);
  }
}
