package com.loadlab.aggregator;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

// Turns the controller's cumulative snapshots ("840 requests so far") into a delta
// time series ("70 requests since the last snapshot"). The persisted history now
// lives in TimescaleDB (via WindowRepository); this class only computes deltas.
@Service
public class WindowAggregator {

  public record Window(
      String testId, Instant timestamp, long requestsInWindow, long errorsInWindow) {}

  // Holds only the LAST cumulative snapshot per test — all that's needed to compute
  // the next delta. Bounded by the number of currently ACTIVE tests, not the whole
  // history (that lives durably in TimescaleDB now).
  private final Map<String, TestResult> lastSeen = new ConcurrentHashMap<>();

  public Window ingest(TestResult cumulative) {
    TestResult previous = lastSeen.put(cumulative.id(), cumulative);
    long prevRequests = previous == null ? 0 : previous.totalRequests();
    long prevErrors = previous == null ? 0 : previous.errors();

    // Clamp at zero: an out-of-order or duplicate delivery could otherwise make a
    // cumulative count APPEAR to shrink, producing a nonsensical negative delta.
    long deltaRequests = Math.max(0, cumulative.totalRequests() - prevRequests);
    long deltaErrors = Math.max(0, cumulative.errors() - prevErrors);

    return new Window(cumulative.id(), Instant.now(), deltaRequests, deltaErrors);
  }
}
