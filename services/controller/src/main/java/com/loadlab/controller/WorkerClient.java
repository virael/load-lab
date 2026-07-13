package com.loadlab.controller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Only a best-effort liveness check remains here — the test trigger and the metrics
// relay now travel over Kafka (CommandPublisher / LoadTestService.onMetrics). The
// direct REST and SSE calls to the worker were removed as dead code.
@Component
public class WorkerClient {

  private final String workerBaseUrl;
  private final HttpClient http = HttpClient.newHttpClient();

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
}
