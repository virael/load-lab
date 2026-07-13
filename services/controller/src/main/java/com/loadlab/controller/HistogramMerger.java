package com.loadlab.controller;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.zip.DataFormatException;
import org.HdrHistogram.Histogram;

// Percentiles from different machines cannot be combined once they are numbers —
// averaging or maxing them is arithmetic on the wrong object. The distributions can
// be combined, so workers ship raw histograms and the real percentiles are computed
// here, from the merged shape.
final class HistogramMerger {
  private HistogramMerger() {}

  record MergedPercentiles(long p50Ms, long p95Ms, long p99Ms, double avgMs) {}

  static MergedPercentiles merge(List<byte[]> encodedHistograms) {
    Histogram merged = new Histogram(1, 60_000, 2);
    for (byte[] bytes : encodedHistograms) {
      if (bytes == null || bytes.length == 0) continue;
      try {
        merged.add(Histogram.decodeFromCompressedByteBuffer(ByteBuffer.wrap(bytes), 0));
      } catch (DataFormatException e) {
        // Both sides of this contract are ours, so an undecodable payload means the
        // encoding is broken, not that a worker sent something unusual. Fail loudly
        // instead of quietly reporting percentiles computed from partial data.
        throw new IllegalStateException("Could not decode a worker's histogram", e);
      }
    }
    return new MergedPercentiles(
        merged.getValueAtPercentile(50.0),
        merged.getValueAtPercentile(95.0),
        merged.getValueAtPercentile(99.0),
        merged.getMean());
  }
}
