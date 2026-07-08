package com.loadlab.controller;

import java.util.concurrent.atomic.AtomicLong;

public class TestMetrics {
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalLatencyNanos = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public void recordRequest(long latencyNanos, boolean isError) {
        totalRequests.incrementAndGet();
        totalLatencyNanos.addAndGet(latencyNanos);
        if (isError)
            errors.incrementAndGet();
    }

    public long totalRequests() {
        return totalRequests.get();
    }

    public long errors() {
        return errors.get();
    }

    public double avgLatencyMs() {
        long total = totalRequests.get();
        return total == 0 ? 0.0 : (totalLatencyNanos.get() / (double) total) / 1_000_000.0;
    }
}