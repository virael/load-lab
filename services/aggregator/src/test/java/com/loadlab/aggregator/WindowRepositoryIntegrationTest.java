package com.loadlab.aggregator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class WindowRepositoryIntegrationTest extends AbstractTimescaleIntegrationTest {

  @Autowired private WindowRepository windowRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void savesAndReadsBackWindowsInOrder() {
    var w1 = new WindowAggregator.Window("cmp-test-1", Instant.now(), 50, 0);
    var w2 = new WindowAggregator.Window("cmp-test-1", Instant.now().plusSeconds(1), 70, 2);

    windowRepository.save(w1);
    windowRepository.save(w2);

    List<WindowAggregator.Window> found = windowRepository.findByTestId("cmp-test-1");

    assertThat(found).hasSize(2);
    assertThat(found.get(0).requestsInWindow()).isEqualTo(50);
    assertThat(found.get(1).requestsInWindow()).isEqualTo(70);
  }

  @Test
  void migrationsRegisteredTheContinuousAggregateAndRetentionPolicy() {
    // Proves the E5.4 config (downsampling + retention) actually took effect on a REAL
    // TimescaleDB — not just that the SQL ran without error, but that the engine
    // registered both policies. A broken V2 migration would fail this precisely.
    Integer caggCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM timescaledb_information.continuous_aggregates "
                + "WHERE view_name = 'metric_windows_1min'",
            Integer.class);
    assertThat(caggCount).isEqualTo(1);

    Integer retentionJobCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM timescaledb_information.jobs "
                + "WHERE proc_name = 'policy_retention'",
            Integer.class);
    assertThat(retentionJobCount).isEqualTo(1);
  }
}
