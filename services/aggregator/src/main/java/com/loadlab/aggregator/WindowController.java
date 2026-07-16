package com.loadlab.aggregator;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// Exists only for manual verification of this step. In E5.2 the windows flow to
// TimescaleDB instead of (or alongside) this in-memory list.
@RestController
public class WindowController {

  private final WindowAggregator windowAggregator;

  public WindowController(WindowAggregator windowAggregator) {
    this.windowAggregator = windowAggregator;
  }

  @GetMapping("/windows/{testId}")
  public List<WindowAggregator.Window> getWindows(@PathVariable String testId) {
    return windowAggregator.windowsFor(testId);
  }
}
