package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;

class HistogramMergerTest {

  private byte[] encode(long... values) {
    Histogram h = new Histogram(1, 60_000, 2);
    for (long v : values) h.recordValue(v);
    ByteBuffer buffer = ByteBuffer.allocate(h.getNeededByteBufferCapacity());
    int len = h.encodeIntoCompressedByteBuffer(buffer);
    byte[] bytes = new byte[len];
    buffer.rewind();
    buffer.get(bytes, 0, len);
    return bytes;
  }

  @Test
  void mergingRevealsATailThatNoSingleWorkerHistogramShows() {
    // Worker A: fast and boring. Its OWN p99 is 10 — nothing to see.
    long[] fastValues = new long[100];
    Arrays.fill(fastValues, 10);
    byte[] workerA = encode(fastValues);

    // Worker B: mostly fast too, but with a handful of very slow outliers.
    long[] slowValues = new long[100];
    Arrays.fill(slowValues, 0, 95, 10);
    Arrays.fill(slowValues, 95, 100, 900);
    byte[] workerB = encode(slowValues);

    var merged = HistogramMerger.merge(List.of(workerA, workerB));

    assertThat(merged.p99Ms()).isGreaterThanOrEqualTo(900);
    assertThat(merged.p50Ms()).isCloseTo(10, within(2L));
  }

  @Test
  void averagingWorkerPercentilesWouldUnderreportTheTail() {
    // The concrete failure the naive E4.3 aggregation had. Same two workers.
    long[] fastValues = new long[100];
    Arrays.fill(fastValues, 10);
    long[] slowValues = new long[100];
    Arrays.fill(slowValues, 0, 95, 10);
    Arrays.fill(slowValues, 95, 100, 900);

    Histogram a = new Histogram(1, 60_000, 2);
    for (long v : fastValues) a.recordValue(v);
    Histogram b = new Histogram(1, 60_000, 2);
    for (long v : slowValues) b.recordValue(v);

    long aP99 = a.getValueAtPercentile(99.0); // ~10
    long bP99 = b.getValueAtPercentile(99.0); // ~900
    long averagedP99 = (aP99 + bP99) / 2; // ~455 — a number nothing actually measured

    var merged = HistogramMerger.merge(List.of(encode(fastValues), encode(slowValues)));

    // Averaging the workers' own p99s invents a value roughly half the real tail.
    assertThat(averagedP99).isLessThan(merged.p99Ms());
    assertThat(merged.p99Ms()).isGreaterThanOrEqualTo(900);
  }

  @Test
  void emptyInputProducesZeroedPercentiles() {
    var merged = HistogramMerger.merge(List.of());
    assertThat(merged.p50Ms()).isZero();
    assertThat(merged.p99Ms()).isZero();
  }

  @Test
  void skipsNullAndEmptyHistograms() {
    // A sub-run that has not reported real data yet sends a null histogram.
    var merged = HistogramMerger.merge(Arrays.asList(null, new byte[0], encode(10, 20, 30)));
    assertThat(merged.p50Ms()).isGreaterThan(0);
  }
}
