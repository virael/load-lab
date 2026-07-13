package com.loadlab.worker;

// `histogram` is the compressed HdrHistogram encoding of this run's full latency
// distribution; Jackson Base64-encodes byte[] into JSON automatically. The
// pre-computed p50/p95/p99 stay for the worker's own standalone debug endpoint —
// the controller ignores them and merges the raw histograms instead.
public record RunResult(
    String id,
    String status,
    long totalRequests,
    double avgLatencyMs,
    long errors,
    long p50Ms,
    long p95Ms,
    long p99Ms,
    byte[] histogram) {}
