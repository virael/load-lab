package com.loadlab.aggregator;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Shared base for aggregator @SpringBootTests: a REAL TimescaleDB, not plain Postgres,
// so the E5.4 migration (create_hypertable, continuous aggregate, retention) actually
// runs. asCompatibleSubstituteFor tells Testcontainers to treat this non-"postgres"
// image as Postgres-protocol-compatible (it is — TimescaleDB is Postgres + extension).
@Testcontainers
abstract class AbstractTimescaleIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer TIMESCALEDB =
      new PostgreSQLContainer(
          DockerImageName.parse("timescale/timescaledb:2.18.0-pg17")
              .asCompatibleSubstituteFor("postgres"));
}
