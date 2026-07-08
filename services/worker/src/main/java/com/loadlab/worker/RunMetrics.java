package com.loadlab.worker;

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
        return new Snapshot(
                cumulative.getValueAtPercentile(50.0),
                cumulative.getValueAtPercentile(95.0),
                cumulative.getValueAtPercentile(99.0),
                cumulative.getMean());
    }

    public record Snapshot(long p50Ms, long p95Ms, long p99Ms, double avgMs) {}
}