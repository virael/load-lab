package com.loadlab.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WindowAggregatorTest {

  @Test
  void firstSnapshotProducesDeltaEqualToItsOwnTotal() {
    var aggregator = new WindowAggregator();
    var window = aggregator.ingest(new TestResult("t1", "RUNNING", 50, 10.0, 0, 5, 8, 9, null));
    assertThat(window.requestsInWindow()).isEqualTo(50);
  }

  @Test
  void subsequentSnapshotProducesOnlyTheNewRequestsSinceLastOne() {
    var aggregator = new WindowAggregator();
    aggregator.ingest(new TestResult("t1", "RUNNING", 50, 10.0, 0, 5, 8, 9, null));

    var window = aggregator.ingest(new TestResult("t1", "RUNNING", 120, 11.0, 2, 5, 8, 9, null));

    assertThat(window.requestsInWindow()).isEqualTo(70);
    assertThat(window.errorsInWindow()).isEqualTo(2);
  }

  @Test
  void neverProducesNegativeDeltaOnOutOfOrderDelivery() {
    var aggregator = new WindowAggregator();
    aggregator.ingest(new TestResult("t1", "RUNNING", 100, 10.0, 0, 5, 8, 9, null));

    var window = aggregator.ingest(new TestResult("t1", "RUNNING", 80, 10.0, 0, 5, 8, 9, null));

    assertThat(window.requestsInWindow()).isZero();
  }

  @Test
  void tracksDifferentTestsIndependently() {
    var aggregator = new WindowAggregator();
    aggregator.ingest(new TestResult("t1", "RUNNING", 100, 0, 0, 0, 0, 0, null));
    aggregator.ingest(new TestResult("t2", "RUNNING", 30, 0, 0, 0, 0, 0, null));

    assertThat(aggregator.windowsFor("t1")).hasSize(1);
    assertThat(aggregator.windowsFor("t2")).hasSize(1);
  }
}
