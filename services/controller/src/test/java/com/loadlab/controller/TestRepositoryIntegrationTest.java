package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class TestRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

  @Autowired private TestRepository testRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void insertPendingWritesARow() {
    var initial = new TestResult("row-test-1", "PENDING", 0, 0.0, 0, 0, 0, 0, null);
    testRepository.insertPending(initial, new TestRequest("http://example.invalid", 10, 5));

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM tests WHERE id = ?", Integer.class, "row-test-1");
    assertThat(count).isEqualTo(1);
  }

  @Test
  void updateProgressReflectsLatestMergedResult() {
    var initial = new TestResult("row-test-2", "PENDING", 0, 0.0, 0, 0, 0, 0, null);
    testRepository.insertPending(initial, new TestRequest("http://example.invalid", 10, 5));

    var done = new TestResult("row-test-2", "DONE", 500, 42.5, 3, 10, 15, 19, null);
    testRepository.updateProgress(done);

    String status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM tests WHERE id = ?", String.class, "row-test-2");
    Long totalRequests =
        jdbcTemplate.queryForObject(
            "SELECT total_requests FROM tests WHERE id = ?", Long.class, "row-test-2");

    assertThat(status).isEqualTo("DONE");
    assertThat(totalRequests).isEqualTo(500L);
  }
}
