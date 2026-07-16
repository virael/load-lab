package com.loadlab.aggregator;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Reads from TimescaleDB, not the in-memory aggregator: comparing a run against one
// from last week needs data that survived a restart, not an ephemeral map.
@RestController
public class WindowController {

  private final WindowRepository windowRepository;

  public WindowController(WindowRepository windowRepository) {
    this.windowRepository = windowRepository;
  }

  @GetMapping("/windows/{testId}")
  public List<WindowAggregator.Window> getWindows(@PathVariable String testId) {
    return windowRepository.findByTestId(testId);
  }
}
