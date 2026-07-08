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
    var snap = metrics.snapshot();

    assertThat(metrics.totalRequests()).isZero();
    assertThat(snap.avgMs()).isZero();
    assertThat(snap.p50Ms()).isZero();
  }

  @Test
  void recordRequestCountsErrorsSeparatelyFromTotal() {
    var metrics = new TestMetrics();

    metrics.recordRequest(5, false);
    metrics.recordRequest(5, true);
    metrics.recordRequest(5, true);

    assertThat(metrics.totalRequests()).isEqualTo(3);
    assertThat(metrics.errors()).isEqualTo(2);
  }

  @Test
  void percentilesRevealTheTailThatAverageHides() {
    var metrics = new TestMetrics();

    for (int i = 0; i < 95; i++) metrics.recordRequest(10, false);
    for (int i = 0; i < 5; i++) metrics.recordRequest(500, false);

    var snap = metrics.snapshot();

    assertThat(snap.p50Ms()).isCloseTo(10, within(2L));
    assertThat(snap.p99Ms()).isGreaterThanOrEqualTo(500);
    assertThat(snap.avgMs()).isLessThan(snap.p99Ms());
  }

  @Test
  void snapshotAccumulatesAcrossMultipleCalls() {
    var metrics = new TestMetrics();

    metrics.recordRequest(10, false);
    metrics.snapshot();

    metrics.recordRequest(20, false);
    var secondSnap = metrics.snapshot();

    assertThat(secondSnap.p50Ms()).isBetween(10L, 20L);
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
              for (int j = 0; j < callsPerThread; j++) metrics.recordRequest(1, false);
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
  }
}
