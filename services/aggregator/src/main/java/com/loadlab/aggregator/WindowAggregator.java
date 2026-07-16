package com.loadlab.aggregator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

// Turns the controller's cumulative snapshots ("840 requests so far") into a delta
// time series ("70 requests since the last snapshot") — the first real
// cumulative-state -> per-window-increment transform in the project. Windows are
// "whatever arrives from Kafka", not clock-aligned buckets; true calendar bucketing
// is the database's job in E5.2.
@Service
public class WindowAggregator {

  public record Window(
      String testId, Instant timestamp, long requestsInWindow, long errorsInWindow) {}

  private final Map<String, TestResult> lastSeen = new ConcurrentHashMap<>();
  private final Map<String, List<Window>> windows = new ConcurrentHashMap<>();

  public Window ingest(TestResult cumulative) {
    TestResult previous = lastSeen.put(cumulative.id(), cumulative);
    long prevRequests = previous == null ? 0 : previous.totalRequests();
    long prevErrors = previous == null ? 0 : previous.errors();

    // Clamp at zero: an out-of-order or duplicate delivery could otherwise make a
    // cumulative count APPEAR to shrink, producing a nonsensical negative
    // "requests this window" value.
    long deltaRequests = Math.max(0, cumulative.totalRequests() - prevRequests);
    long deltaErrors = Math.max(0, cumulative.errors() - prevErrors);

    Window window = new Window(cumulative.id(), Instant.now(), deltaRequests, deltaErrors);
    windows.computeIfAbsent(cumulative.id(), k -> new CopyOnWriteArrayList<>()).add(window);
    return window;
  }

  public List<Window> windowsFor(String testId) {
    return windows.getOrDefault(testId, List.of());
  }
}
