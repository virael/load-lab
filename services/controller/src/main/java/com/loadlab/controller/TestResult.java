package com.loadlab.controller;

public record TestResult(
    String id,
    String status, // PENDING, RUNNING, DONE -> TODO: Use enum
    long totalRequests,
    double avgLatencyMs,
    long errors) {}
