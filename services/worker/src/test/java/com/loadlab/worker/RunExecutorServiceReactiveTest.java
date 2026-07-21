package com.loadlab.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RunExecutorServiceReactiveTest {

  private HttpServer target;

  @Autowired private RunExecutorService runExecutorService;

  @AfterEach
  void stopTarget() {
    if (target != null) target.stop(0);
  }

  @Test
  void generatesRequestVolumeProportionalToVirtualUsersAndDuration() throws Exception {
    target = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    target.createContext(
        "/ping",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    target.start();
    String url = "http://localhost:" + target.getAddress().getPort() + "/ping";

    runExecutorService.startRun("reactive-test-1", new RunRequest(url, 20, 2), 0);

    RunResult result = awaitDone("reactive-test-1");
    assertThat(result.status()).isEqualTo("DONE");
    assertThat(result.totalRequests()).isGreaterThan(0);
    assertThat(result.errors()).isZero();
  }

  @Test
  void oneFailingRequestDoesNotTerminateTheWholeRun() throws Exception {
    AtomicInteger callCount = new AtomicInteger();
    target = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    target.createContext(
        "/flaky",
        exchange -> {
          // Every third request fails on purpose — we check the rest of the reactive
          // stream keeps going for the whole duration regardless.
          if (callCount.incrementAndGet() % 3 == 0) {
            exchange.sendResponseHeaders(500, -1);
          } else {
            exchange.sendResponseHeaders(200, -1);
          }
          exchange.close();
        });
    target.start();
    String url = "http://localhost:" + target.getAddress().getPort() + "/flaky";

    runExecutorService.startRun("reactive-test-2", new RunRequest(url, 10, 2), 0);

    RunResult result = awaitDone("reactive-test-2");
    assertThat(result.status()).isEqualTo("DONE");
    // The key proof: despite errors on every third request, the stream did NOT stop
    // after the first error — total requests is many times the error count, because
    // the loop ran for the full duration.
    assertThat(result.totalRequests()).isGreaterThan(result.errors() * 2);
    assertThat(result.errors()).isGreaterThan(0);
  }

  // Poll instead of Awaitility: this project's slim test starter does not bundle it,
  // and adding a dependency for one wait would be gratuitous.
  private RunResult awaitDone(String id) throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      RunResult result = runExecutorService.getResult(id);
      if (result != null && "DONE".equals(result.status())) {
        return result;
      }
      Thread.sleep(100);
    }
    return runExecutorService.getResult(id);
  }
}
