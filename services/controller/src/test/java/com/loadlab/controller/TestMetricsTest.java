package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TestMetricsTest {

  @Test
  void startsAtZero() {
    var metrics = new TestMetrics();

    assertThat(metrics.totalRequests()).isZero();
    assertThat(metrics.errors()).isZero();
    assertThat(metrics.avgLatencyMs()).isZero();
  }

  @Test
  void recordRequestAccumulatesCountAndLatency() {
    var metrics = new TestMetrics();

    metrics.recordRequest(10_000_000, false); // 10ms
    metrics.recordRequest(20_000_000, false); // 20ms

    assertThat(metrics.totalRequests()).isEqualTo(2);
    assertThat(metrics.avgLatencyMs()).isCloseTo(15.0, within(0.01));
  }

  @Test
  void recordRequestCountsErrorsSeparatelyFromTotal() {
    var metrics = new TestMetrics();

    metrics.recordRequest(5_000_000, false);
    metrics.recordRequest(5_000_000, true);
    metrics.recordRequest(5_000_000, true);

    assertThat(metrics.totalRequests()).isEqualTo(3);
    assertThat(metrics.errors()).isEqualTo(2);
  }

  @Test
  void survivesConcurrentRecordingWithoutLosingUpdates() throws InterruptedException {
    var metrics = new TestMetrics();
    int threads = 100;
    int callsPerThread = 100;
    var pool = Executors.newFixedThreadPool(threads);
    var start = new CountDownLatch(1);
    var done = new CountDownLatch(threads);

    for (int i = 0; i < threads; i++) {
      pool.submit(
          () -> {
            try {
              start.await();

              for (int j = 0; j < callsPerThread; j++) {
                metrics.recordRequest(1_000_000, false);
              }
            } catch (InterruptedException ignored) {
            } finally {
              done.countDown();
            }
          });
    }

    start.countDown();
    done.await(5, TimeUnit.SECONDS);
    pool.shutdown();

    assertThat(metrics.totalRequests()).isEqualTo((long) threads * callsPerThread);
    assertThat(metrics.avgLatencyMs()).isCloseTo(1.0, within(0.01));
  }
}
