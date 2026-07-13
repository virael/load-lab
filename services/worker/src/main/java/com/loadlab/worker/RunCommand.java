package com.loadlab.worker;

// Deliberate duplicate of the controller's RunCommand: the two services share a wire
// contract, not a codebase. Type headers are disabled on both sides so the
// controller's class name never leaks into the worker's deserialization.
public record RunCommand(
    String subId, String targetUrl, int virtualUsers, int durationSeconds, long rampDelayMs) {}
