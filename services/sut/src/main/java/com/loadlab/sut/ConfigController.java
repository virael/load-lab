package com.loadlab.sut;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/simulate/config")
public class ConfigController {

  private final SimulationState state;

  public ConfigController(SimulationState state) {
    this.state = state;
  }

  @GetMapping
  public ConfigRequest getConfig() {
    return new ConfigRequest(state.minLatencyMs(), state.maxLatencyMs(), state.errorRate());
  }

  @PostMapping
  public ResponseEntity<?> updateConfig(@RequestBody ConfigRequest request) {
    if (request.errorRate() != null && (request.errorRate() < 0 || request.errorRate() > 1)) {
      return ResponseEntity.badRequest().body("errorRate must be between 0 and 1");
    }
    long effectiveMin =
        request.minLatencyMs() != null ? request.minLatencyMs() : state.minLatencyMs();
    long effectiveMax =
        request.maxLatencyMs() != null ? request.maxLatencyMs() : state.maxLatencyMs();
    if (effectiveMin > effectiveMax) {
      return ResponseEntity.badRequest().body("minLatencyMs must be <= maxLatencyMs");
    }
    state.update(request.minLatencyMs(), request.maxLatencyMs(), request.errorRate());
    return ResponseEntity.ok(getConfig());
  }
}
