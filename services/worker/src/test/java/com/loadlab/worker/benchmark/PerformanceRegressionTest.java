package com.loadlab.worker.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loadlab.worker.RunExecutorService;
import com.loadlab.worker.RunRequest;
import com.loadlab.worker.RunResult;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Performance regression gate — the load-testing tool measuring its own performance.
 *
 * <p>Deliberately calls the real {@link RunExecutorService#startRun}, not a
 * reimplemented engine: an accidental blocking call in makeRequest() or a changed
 * connection-pool setting in WebClientConfig would show up here, which a copy of the
 * engine could never catch.
 *
 * <p>Excluded from the default {@code mvn test}/{@code verify} (see
 * {@code perf.excludedGroups} in pom.xml) so local dev and ordinary CI stay fast; it
 * runs as a separate, explicit CI job to keep the runner-hardware noise out of the
 * normal build.
 */
@Tag("performance")
@SpringBootTest
class PerformanceRegressionTest {

  private static final int VIRTUAL_USERS = 100;
  private static final int DURATION_SECONDS = 5;
  // GitHub Actions runners have highly variable, shared CPU, so the margin is
  // deliberately generous. The goal is to catch a REAL regression (a careless
  // blocking call, unnecessary overhead), not to chase noise between runner
  // instances.
  private static final double REGRESSION_THRESHOLD = 0.5; // +50% over baseline

  private HttpServer stubTarget;

  @Autowired private RunExecutorService runExecutorService;

  @AfterEach
  void stopTarget() {
    if (stubTarget != null) stubTarget.stop(0);
  }

  @Test
  void reactiveEngineDoesNotRegressBeyondBaseline() throws Exception {
    stubTarget = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    stubTarget.createContext(
        "/ping",
        exchange -> {
          // Small, constant overhead — we measure the WORKER's engine, not a real
          // SUT whose behaviour could drift independently.
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    stubTarget.start();
    String url = "http://localhost:" + stubTarget.getAddress().getPort() + "/ping";

    runExecutorService.startRun(
        "perf-regression-1", new RunRequest(url, VIRTUAL_USERS, DURATION_SECONDS), 0);

    // Poll instead of Awaitility: this module's slim test starter does not bundle it.
    long currentP99 = awaitDone("perf-regression-1").p99Ms();

    // Written here, before any early return or assertion, so the data point always
    // lands on disk — a run that FAILED the gate is as valuable on the trend chart as
    // one that passed.
    writeBenchmarkOutput(currentP99);

    if (Boolean.getBoolean("benchmark.recordBaseline")) {
      System.out.printf(
          "%n>>> Measured p99: %dms — paste this value by hand into "
              + "performance-baseline.json. <<<%n%n",
          currentP99);
      return; // recording mode: no assertion, report only
    }

    long baselineP99 = readBaselineP99();
    long allowedMax = (long) (baselineP99 * (1 + REGRESSION_THRESHOLD));

    System.out.printf(
        "Baseline p99: %dms | Current p99: %dms | Allowed max: %dms%n",
        baselineP99, currentP99, allowedMax);

    assertThat(currentP99)
        .withFailMessage(
            "Performance regression detected: p99 = %dms, baseline = %dms "
                + "(allowed up to %dms, +%.0f%% margin for CI runner noise)",
            currentP99, baselineP99, allowedMax, REGRESSION_THRESHOLD * 100)
        .isLessThanOrEqualTo(allowedMax);
  }

  private void writeBenchmarkOutput(long p99Ms) throws Exception {
    // The [{name, unit, value}] shape github-action-benchmark expects in "custom" mode
    // — one entry per tracked metric. Relative path resolves to the module dir
    // (services/worker) where Maven runs the test, which is where the CI job reads it.
    String json =
        String.format(
            "[{\"name\": \"Worker p99 latency (%d VU)\", \"unit\": \"ms\", \"value\": %d}]",
            VIRTUAL_USERS, p99Ms);
    Files.writeString(Path.of("benchmark-output.json"), json);
  }

  private long readBaselineP99() throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/performance-baseline.json")) {
      return new ObjectMapper().readTree(in).get("p99Ms").asLong();
    }
  }

  private RunResult awaitDone(String id) throws InterruptedException {
    for (int i = 0; i < 300; i++) {
      RunResult result = runExecutorService.getResult(id);
      if (result != null && "DONE".equals(result.status())) {
        return result;
      }
      Thread.sleep(100);
    }
    return runExecutorService.getResult(id);
  }
}
