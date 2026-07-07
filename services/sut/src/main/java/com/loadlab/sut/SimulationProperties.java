package com.loadlab.sut;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sut")
public record SimulationProperties(
    long latencyMs,
    double errorRate
) {}