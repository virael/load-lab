package com.loadlab.controller;

import java.time.Instant;

// Historical view of a test run, read back from Postgres — includes the request
// parameters and created_at that the live TestResult (in-memory hot path) omits.
public record TestSummary(
    String id,
    String targetUrl,
    int virtualUsers,
    int durationSeconds,
    String status,
    long totalRequests,
    double avgLatencyMs,
    long errors,
    long p50Ms,
    long p95Ms,
    long p99Ms,
    Instant createdAt) {}
