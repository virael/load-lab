package com.loadlab.aggregator;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ResultListener {

  private final WindowAggregator windowAggregator;

  public ResultListener(WindowAggregator windowAggregator) {
    this.windowAggregator = windowAggregator;
  }

  @KafkaListener(topics = "test-results", groupId = "aggregator-group")
  public void onResult(TestResult result) {
    windowAggregator.ingest(result);
  }
}
