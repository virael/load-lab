package com.loadlab.worker;

public record RunResult(
    String id,
    String status,
    long totalRequests,
    double avgLatencyMs,
    long errors,
    long p50Ms,
    long p95Ms,
    long p99Ms
) {}