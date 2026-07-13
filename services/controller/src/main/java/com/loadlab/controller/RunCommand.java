package com.loadlab.controller;

public record RunCommand(String testId, String targetUrl, int virtualUsers, int durationSeconds) {}
