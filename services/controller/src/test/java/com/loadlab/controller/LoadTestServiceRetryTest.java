package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LoadTestServiceRetryTest {

  private HttpServer fakeWorker;

  @AfterEach
  void stopFakeWorker() {
    if (fakeWorker != null) fakeWorker.stop(0);
  }

  @Test
  void retriesTransientFailuresBeforeSucceeding() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    fakeWorker = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    fakeWorker.createContext(
        "/actuator/health",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    fakeWorker.createContext(
        "/runs",
        exchange -> {
          if (attempts.incrementAndGet() < 3) {
            exchange.sendResponseHeaders(500, -1); // simulate a transient failure twice
            exchange.close();
            return;
          }
          String body =
              "{\"id\":\"ok-run\",\"status\":\"PENDING\",\"totalRequests\":0,"
                  + "\"avgLatencyMs\":0.0,\"errors\":0,\"p50Ms\":0,\"p95Ms\":0,\"p99Ms\":0}";
          byte[] bytes = body.getBytes();
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    fakeWorker.start();

    var workerClient = new WorkerClient("http://localhost:" + fakeWorker.getAddress().getPort());
    var service = new LoadTestService(workerClient);

    TestResult result = service.startTest(new TestRequest("http://example.invalid", 1, 1));

    assertThat(attempts.get()).isEqualTo(3);
    assertThat(result.id()).isEqualTo("ok-run");
  }

  @Test
  void abortsAfterExhaustingRetries() throws Exception {
    fakeWorker = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    fakeWorker.createContext(
        "/actuator/health",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    fakeWorker.createContext(
        "/runs",
        exchange -> {
          exchange.sendResponseHeaders(500, -1); // always fails
          exchange.close();
        });
    fakeWorker.start();

    var workerClient = new WorkerClient("http://localhost:" + fakeWorker.getAddress().getPort());
    var service = new LoadTestService(workerClient);

    assertThatThrownBy(() -> service.startTest(new TestRequest("http://example.invalid", 1, 1)))
        .isInstanceOf(WorkerUnavailableException.class);
  }
}
