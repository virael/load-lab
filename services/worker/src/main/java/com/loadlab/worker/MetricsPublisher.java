package com.loadlab.worker;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MetricsPublisher {

  public static final String TOPIC = "test-metrics";

  private final KafkaTemplate<String, RunResult> kafkaTemplate;

  public MetricsPublisher(KafkaTemplate<String, RunResult> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void publish(RunResult result) {
    kafkaTemplate.send(TOPIC, result.id(), result);
  }
}
