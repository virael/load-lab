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

    var t1Second = aggregator.ingest(new TestResult("t1", "RUNNING", 150, 0, 0, 0, 0, 0, null));
    var t2Second = aggregator.ingest(new TestResult("t2", "RUNNING", 50, 0, 0, 0, 0, 0, null));

    assertThat(t1Second.requestsInWindow()).isEqualTo(50);
    assertThat(t2Second.requestsInWindow()).isEqualTo(20);
  }
}
