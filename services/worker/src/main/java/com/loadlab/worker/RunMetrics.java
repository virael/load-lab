package com.loadlab.worker;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

public class RunMetrics {

  private final Recorder recorder = new Recorder(1, 60_000, 2);
  private final Histogram cumulative = new Histogram(1, 60_000, 2);

  private final AtomicLong totalRequests = new AtomicLong();
  private final AtomicLong errors = new AtomicLong();

  public void recordRequest(long latencyMs, boolean isError) {
    totalRequests.incrementAndGet();
    if (isError) errors.incrementAndGet();
    recorder.recordValue(Math.max(1, latencyMs));
  }

  public long totalRequests() {
    return totalRequests.get();
  }

  public long errors() {
    return errors.get();
  }

  public synchronized Snapshot snapshot() {
    Histogram interval = recorder.getIntervalHistogram();
    cumulative.add(interval);

    // Encode the whole cumulative distribution, not just the percentiles computed
    // from it, so the controller can MERGE this with other workers' histograms
    // before computing a true global percentile. Percentiles cannot be combined
    // after the fact; the underlying shape can. Encoding stays inside this
    // synchronized block and reuses the already-merged `cumulative` — reaching for
    // the recorder again would drain the interval a second time and yield garbage.
    ByteBuffer buffer = ByteBuffer.allocate(cumulative.getNeededByteBufferCapacity());
    int len = cumulative.encodeIntoCompressedByteBuffer(buffer);
    byte[] histogramBytes = new byte[len];
    buffer.rewind();
    buffer.get(histogramBytes, 0, len);

    return new Snapshot(
        cumulative.getValueAtPercentile(50.0),
        cumulative.getValueAtPercentile(95.0),
        cumulative.getValueAtPercentile(99.0),
        cumulative.getMean(),
        histogramBytes);
  }

  public record Snapshot(long p50Ms, long p95Ms, long p99Ms, double avgMs, byte[] histogramBytes) {}
}
