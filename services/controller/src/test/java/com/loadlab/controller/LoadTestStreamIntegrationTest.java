package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadTestStreamIntegrationTest {

  @LocalServerPort int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void streamsGrowingMetricsUntilDone() throws Exception {
    // Minimal in-process target server — no external network dependency,
    // so this test is fast and deterministic regardless of CI environment.
    HttpServer target = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    target.createContext(
        "/ping",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    target.start();

    try {
      int targetPort = target.getAddress().getPort();
      String startBody =
          """
                {"targetUrl":"http://localhost:%d/ping","virtualUsers":5,"durationSeconds":3}
                """
              .formatted(targetPort);

      HttpRequest startReq =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/tests"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(startBody))
              .build();
      HttpResponse<String> startResp = http.send(startReq, HttpResponse.BodyHandlers.ofString());
      String id = mapper.readTree(startResp.body()).get("id").asText();

      HttpRequest streamReq =
          HttpRequest.newBuilder(
                  URI.create("http://localhost:" + port + "/tests/" + id + "/stream"))
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
          if (!snapshots.isEmpty() && "DONE".equals(snapshots.get(snapshots.size() - 1).status())) {
            break;
          }
        }
      }

      // Proof that data actually streams live, not just a single final response.
      assertThat(snapshots).hasSizeGreaterThanOrEqualTo(2);
      assertThat(snapshots.get(snapshots.size() - 1).status()).isEqualTo("DONE");
      assertThat(snapshots.get(snapshots.size() - 1).totalRequests()).isGreaterThan(0);

      // Metrics must never go backwards between consecutive snapshots.
      for (int i = 1; i < snapshots.size(); i++) {
        assertThat(snapshots.get(i).totalRequests())
            .isGreaterThanOrEqualTo(snapshots.get(i - 1).totalRequests());
      }
    } finally {
      target.stop(0);
    }
  }
}
