package com.loadlab.controller;

// Keyed by subId, never testId: Kafka routes equal keys to the same partition, so
// keying by testId would funnel every slice of one test into a single worker and
// silently defeat the point of splitting it at all.
public record RunCommand(
    String subId, String targetUrl, int virtualUsers, int durationSeconds, long rampDelayMs) {}
