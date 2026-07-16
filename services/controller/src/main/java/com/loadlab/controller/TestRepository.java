package com.loadlab.controller;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TestRepository {

  private final JdbcTemplate jdbcTemplate;

  public TestRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void insertPending(TestResult initial, TestRequest req) {
    jdbcTemplate.update(
        "INSERT INTO tests (id, target_url, virtual_users, duration_seconds, status) "
            + "VALUES (?, ?, ?, ?, ?)",
        initial.id(),
        req.targetUrl(),
        req.virtualUsers(),
        req.durationSeconds(),
        initial.status());
  }

  public void updateProgress(TestResult result) {
    jdbcTemplate.update(
        "UPDATE tests SET status = ?, total_requests = ?, avg_latency_ms = ?, errors = ?, "
            + "p50_ms = ?, p95_ms = ?, p99_ms = ?, updated_at = now() WHERE id = ?",
        result.status(),
        result.totalRequests(),
        result.avgLatencyMs(),
        result.errors(),
        result.p50Ms(),
        result.p95Ms(),
        result.p99Ms(),
        result.id());
  }

  public List<TestSummary> findRecent(int limit) {
    return jdbcTemplate.query(
        "SELECT id, target_url, virtual_users, duration_seconds, status, total_requests, "
            + "avg_latency_ms, errors, p50_ms, p95_ms, p99_ms, created_at "
            + "FROM tests ORDER BY created_at DESC LIMIT ?",
        (rs, rowNum) ->
            new TestSummary(
                rs.getString("id"),
                rs.getString("target_url"),
                rs.getInt("virtual_users"),
                rs.getInt("duration_seconds"),
                rs.getString("status"),
                rs.getLong("total_requests"),
                rs.getDouble("avg_latency_ms"),
                rs.getLong("errors"),
                rs.getLong("p50_ms"),
                rs.getLong("p95_ms"),
                rs.getLong("p99_ms"),
                rs.getTimestamp("created_at").toInstant()),
        limit);
  }
}
