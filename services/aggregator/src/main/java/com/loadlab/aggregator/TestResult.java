package com.loadlab.aggregator;

// Own copy of the controller's merged-result DTO — the same duplicate-DTO contract
// used across every service here (deserialization matches JSON field names, type
// headers are off). The `histogram` field is unused for windowing but must exist so
// deserialization of the controller's payload shape lines up; Jackson ignores it.
public record TestResult(
    String id,
    String status,
    long totalRequests,
    double avgLatencyMs,
    long errors,
    long p50Ms,
    long p95Ms,
    long p99Ms,
    byte[] histogram) {}
