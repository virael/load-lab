package com.loadlab.controller;

import com.fasterxml.jackson.annotation.JsonInclude;

// Field-for-field mirror of the worker's RunResult: deserialization matches on JSON
// field names, not class names (type headers are off on both sides).
//
// `histogram` carries the raw distribution INBOUND from a worker. On the merged
// result the controller sends OUTBOUND over SSE it is null — the frontend needs the
// computed numbers, not megabytes of raw histogram — and NON_NULL keeps it out of
// the JSON entirely rather than emitting "histogram":null.
@JsonInclude(JsonInclude.Include.NON_NULL)
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
