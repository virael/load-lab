package com.loadlab.sut;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class SimulationState {

  /** Immutable min/max pair so both bounds always move together atomically. */
  private record Latency(long min, long max) {}

  private final AtomicReference<Latency> latency;
  private final AtomicReference<Double> errorRate;

  public SimulationState(SimulationProperties defaults) {
    this.latency =
        new AtomicReference<>(new Latency(defaults.minLatencyMs(), defaults.maxLatencyMs()));
    this.errorRate = new AtomicReference<>(defaults.errorRate());
  }

  public long minLatencyMs() {
    return latency.get().min();
  }

  public long maxLatencyMs() {
    return latency.get().max();
  }

  public double errorRate() {
    return errorRate.get();
  }

  public void update(Long newMin, Long newMax, Double newErrorRate) {
    if (newMin != null || newMax != null) {
      latency.updateAndGet(
          cur ->
              new Latency(
                  newMin != null ? newMin : cur.min(), newMax != null ? newMax : cur.max()));
    }
    if (newErrorRate != null) errorRate.set(newErrorRate);
  }
}
