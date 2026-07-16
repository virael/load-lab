package com.loadlab.aggregator;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WindowRepository {

  private final JdbcTemplate jdbcTemplate;

  public WindowRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void save(WindowAggregator.Window window) {
    jdbcTemplate.update(
        "INSERT INTO metric_windows (test_id, ts, requests_in_window, errors_in_window) VALUES (?, ?, ?, ?)",
        window.testId(),
        Timestamp.from(window.timestamp()),
        window.requestsInWindow(),
        window.errorsInWindow());
  }
}
