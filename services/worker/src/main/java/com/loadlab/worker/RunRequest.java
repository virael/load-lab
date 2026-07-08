package com.loadlab.worker;

public record RunRequest(
    String targetUrl,
    int virtualUsers,
    int durationSeconds
) {}