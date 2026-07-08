package com.loadlab.controller;

public record TestResult(
    String id,
    String status,
    long totalRequests,
    double avgLatencyMs,
    long errors,
    long p50Ms,
    long p95Ms,
    long p99Ms) {}
