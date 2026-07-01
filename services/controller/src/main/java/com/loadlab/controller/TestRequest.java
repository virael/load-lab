package com.loadlab.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record TestRequest(
    @NotBlank String targetUrl,
    @Positive int virtualUsers,
    @Positive int durationSeconds
) {}