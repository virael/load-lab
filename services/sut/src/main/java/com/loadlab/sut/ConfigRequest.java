package com.loadlab.sut;

public record ConfigRequest(Long minLatencyMs, Long maxLatencyMs, Double errorRate) {}
