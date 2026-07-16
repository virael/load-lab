package com.loadlab.controller;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Shared base for every @SpringBootTest in this module: the controller now requires a
// DataSource and runs Flyway on context start, so each full-context test needs a real
// Postgres. @ServiceConnection lets Boot wire spring.datasource.* from the container
// automatically — no @DynamicPropertySource needed.
@Testcontainers
abstract class AbstractPostgresIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17");
}
