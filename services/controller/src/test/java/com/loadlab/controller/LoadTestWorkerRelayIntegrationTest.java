package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

// Emulates the WORKER's REST + SSE contract with canned responses, so this
// test verifies the controller's RELAY logic in isolation. Real cross-service
// testing with an actual worker process is deferred to Phase 4 (Testcontainers).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadTestWorkerRelayIntegrationTest {

  private static HttpServer fakeWorker;

  @BeforeAll
  static void startFakeWorker() throws IOException {
    fakeWorker = HttpServer.create(new InetSocketAddress("localhost", 0), 0);

    fakeWorker.createContext(
        "/runs",
        exchange ->
            sendJson(
                exchange,
                """
                {"id":"fake-run-1","status":"PENDING","totalRequests":0,
                 "avgLatencyMs":0.0,"errors":0,"p50Ms":0,"p95Ms":0,"p99Ms":0}"""));

    fakeWorker.createContext(
        "/runs/fake-run-1/stream",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
          exchange.sendResponseHeaders(200, 0);
          try (OutputStream out = exchange.getResponseBody()) {
            writeSnapshot(out, "RUNNING", 10);
            writeSnapshot(out, "RUNNING", 20);
            writeSnapshot(out, "DONE", 30);
          }
        });

    fakeWorker.start();
  }

  @AfterAll
  static void stopFakeWorker() {
    fakeWorker.stop(0);
  }

  @DynamicPropertySource
  static void workerUrl(DynamicPropertyRegistry registry) {
    registry.add("worker.base-url", () -> "http://localhost:" + fakeWorker.getAddress().getPort());
  }

  private static void sendJson(HttpExchange exchange, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  private static void writeSnapshot(OutputStream out, String status, long totalRequests)
      throws IOException {
    String json =
        """
                {"id":"fake-run-1","status":"%s","totalRequests":%d,
                 "avgLatencyMs":12.0,"errors":0,"p50Ms":10,"p95Ms":15,"p99Ms":19}"""
            .formatted(status, totalRequests)
            .replace("\n", " ");
    out.write(("event:snapshot\ndata:" + json + "\n\n").getBytes(StandardCharsets.UTF_8));
    out.flush();
  }

  @LocalServerPort int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void relaysWorkerSnapshotsToOwnSubscribers() throws Exception {
    HttpRequest startReq =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/tests"))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"targetUrl\":\"http://example.invalid\",\"virtualUsers\":1,\"durationSeconds\":1}"))
            .build();
    HttpResponse<String> startResp = http.send(startReq, HttpResponse.BodyHandlers.ofString());
    String id = mapper.readTree(startResp.body()).get("id").asText();
    assertThat(id).isEqualTo("fake-run-1");

    HttpRequest streamReq =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/tests/" + id + "/stream"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    HttpResponse<Stream<String>> streamResp =
        http.send(streamReq, HttpResponse.BodyHandlers.ofLines());

    List<TestResult> snapshots = new ArrayList<>();
    StringBuilder dataLine = new StringBuilder();
    for (String line : (Iterable<String>) streamResp.body()::iterator) {
      if (line.startsWith("data:")) {
        dataLine.append(line.substring(5).trim());
      } else if (line.isBlank() && !dataLine.isEmpty()) {
        snapshots.add(mapper.readValue(dataLine.toString(), TestResult.class));
        dataLine.setLength(0);
        if ("DONE".equals(snapshots.get(snapshots.size() - 1).status())) break;
      }
    }

    assertThat(snapshots).hasSize(3);
    assertThat(snapshots.get(2).status()).isEqualTo("DONE");
    assertThat(snapshots.get(2).totalRequests()).isEqualTo(30);
  }
}
