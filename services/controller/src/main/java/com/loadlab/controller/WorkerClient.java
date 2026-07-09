package com.loadlab.controller;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class WorkerClient {

  private final String workerBaseUrl;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();

  public WorkerClient(@Value("${worker.base-url}") String workerBaseUrl) {
    this.workerBaseUrl = workerBaseUrl;
  }

  public boolean isHealthy() {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(workerBaseUrl + "/actuator/health"))
              .timeout(Duration.ofSeconds(2))
              .GET()
              .build();
      HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
      return response.statusCode() == 200;
    } catch (Exception e) {
      return false;
    }
  }

  public TestResult startRun(TestRequest req) throws IOException, InterruptedException {
    String body = mapper.writeValueAsString(req);
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(workerBaseUrl + "/runs"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() / 100 != 2) {
      throw new IOException("Worker responded with status " + response.statusCode());
    }
    return mapper.readValue(response.body(), TestResult.class);
  }

  // Blocks the calling thread until the worker's stream reports a terminal
  // status.
  // Must always be called from a background thread, never from a request-handling
  // one.
  public void streamRun(String runId, Consumer<TestResult> onSnapshot)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(workerBaseUrl + "/runs/" + runId + "/stream"))
            .timeout(Duration.ofMinutes(10))
            .GET()
            .build();
    HttpResponse<Stream<String>> response = http.send(request, HttpResponse.BodyHandlers.ofLines());

    StringBuilder dataLine = new StringBuilder();
    for (String line : (Iterable<String>) response.body()::iterator) {
      if (line.startsWith("data:")) {
        dataLine.append(line.substring(5).trim());
      } else if (line.isBlank() && !dataLine.isEmpty()) {
        TestResult snapshot = mapper.readValue(dataLine.toString(), TestResult.class);
        dataLine.setLength(0);
        onSnapshot.accept(snapshot);
        if ("DONE".equals(snapshot.status())) return;
      }
    }
  }
}
