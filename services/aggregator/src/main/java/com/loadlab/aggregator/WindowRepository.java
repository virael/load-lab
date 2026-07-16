package com.loadlab.aggregator;

import java.sql.Timestamp;
import java.util.List;
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

  public List<WindowAggregator.Window> findByTestId(String testId) {
    return jdbcTemplate.query(
        "SELECT test_id, ts, requests_in_window, errors_in_window FROM metric_windows "
            + "WHERE test_id = ? ORDER BY ts",
        (rs, rowNum) ->
            new WindowAggregator.Window(
                rs.getString("test_id"),
                rs.getTimestamp("ts").toInstant(),
                rs.getLong("requests_in_window"),
                rs.getLong("errors_in_window")),
        testId);
  }
}
