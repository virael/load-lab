package com.loadlab.controller;

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
}
