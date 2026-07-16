package com.loadlab.aggregator;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ResultListener {

  private final WindowAggregator windowAggregator;
  private final WindowRepository windowRepository;

  public ResultListener(WindowAggregator windowAggregator, WindowRepository windowRepository) {
    this.windowAggregator = windowAggregator;
    this.windowRepository = windowRepository;
  }

  // WindowAggregator stays a pure, Spring-free class (unit-tested without a DB); the
  // persistence side-effect lives out here in the Spring-managed component. Injecting
  // the repository into the aggregator would forfeit that isolation.
  @KafkaListener(topics = "test-results", groupId = "aggregator-group")
  public void onResult(TestResult result) {
    WindowAggregator.Window window = windowAggregator.ingest(result);
    windowRepository.save(window);
  }
}
