package com.loadlab.sut;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulationController {

  private final SimulationState state;

  public SimulationController(SimulationState state) {
    this.state = state;
  }

  @GetMapping("/simulate")
  public ResponseEntity<String> simulate() throws InterruptedException {
    long min = state.minLatencyMs();
    long max = state.maxLatencyMs();
    long latency = (min == max) ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
    Thread.sleep(latency);

    boolean shouldFail = ThreadLocalRandom.current().nextDouble() < state.errorRate();
    if (shouldFail) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("simulated failure");
    }
    return ResponseEntity.ok("ok");
  }
}
