package com.loadlab.controller;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LoadTestService {

  private final Map<String, TestResult> store = new ConcurrentHashMap<>();
  private final Map<String, TestMetrics> liveMetrics = new ConcurrentHashMap<>();
  private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

  private final ExecutorService orchestrator = Executors.newCachedThreadPool();
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  public TestResult startTest(TestRequest req) {
    String id = UUID.randomUUID().toString();
    store.put(id, new TestResult(id, "PENDING", 0, 0.0, 0));
    liveMetrics.put(id, new TestMetrics());
    emitters.put(id, new CopyOnWriteArrayList<>());
    orchestrator.submit(() -> runTest(id, req));
    return store.get(id);
  }

  public TestResult getResult(String id) {
    TestMetrics metrics = liveMetrics.get(id);
    if (metrics != null) {
      return new TestResult(
          id, "RUNNING", metrics.totalRequests(), metrics.avgLatencyMs(), metrics.errors());
    }
    return store.get(id);
  }

  public SseEmitter subscribe(String id) {
    SseEmitter emitter = new SseEmitter(0L);
    var list = emitters.get(id);
    if (list == null) {
      emitter.complete();
      return emitter;
    }
    list.add(emitter);
    emitter.onCompletion(() -> list.remove(emitter));
    emitter.onTimeout(() -> list.remove(emitter));
    return emitter;
  }

  @Scheduled(fixedRate = 1000)
  void broadcastSnapshots() {
    for (String id : liveMetrics.keySet()) {
      TestMetrics metrics = liveMetrics.get(id);
      var list = emitters.get(id);
      if (metrics == null || list == null || list.isEmpty()) continue;

      TestResult snapshot =
          new TestResult(
              id, "RUNNING", metrics.totalRequests(), metrics.avgLatencyMs(), metrics.errors());

      for (SseEmitter emitter : list) {
        try {
          emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException e) {
          list.remove(emitter);
        }
      }
    }
  }

  private void runTest(String id, TestRequest req) {
    TestMetrics metrics = liveMetrics.get(id);
    store.put(id, new TestResult(id, "RUNNING", 0, 0.0, 0));

    ExecutorService pool = Executors.newFixedThreadPool(req.virtualUsers());
    long endTime = System.nanoTime() + req.durationSeconds() * 1_000_000_000L;

    HttpRequest request =
        HttpRequest.newBuilder(URI.create(req.targetUrl()))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

    List<Future<?>> vus = new ArrayList<>();
    for (int i = 0; i < req.virtualUsers(); i++) {
      vus.add(
          pool.submit(
              () -> {
                while (System.nanoTime() < endTime) {
                  long start = System.nanoTime();
                  boolean isError = false;
                  try {
                    HttpResponse<Void> resp =
                        http.send(request, HttpResponse.BodyHandlers.discarding());
                    isError = resp.statusCode() >= 400;
                  } catch (Exception e) {
                    isError = true;
                  } finally {
                    metrics.recordRequest(System.nanoTime() - start, isError);
                  }
                }
              }));
    }

    for (Future<?> f : vus) {
      try {
        f.get();
      } catch (Exception ignored) {
      }
    }
    pool.shutdown();

    TestResult finalResult =
        new TestResult(
            id, "DONE", metrics.totalRequests(), metrics.avgLatencyMs(), metrics.errors());
    store.put(id, finalResult);

    var list = emitters.get(id);
    if (list != null) {
      for (SseEmitter emitter : list) {
        try {
          emitter.send(SseEmitter.event().name("snapshot").data(finalResult));
          emitter.complete();
        } catch (IOException e) {
          emitter.completeWithError(e);
        }
      }
    }
    liveMetrics.remove(id);
    emitters.remove(id);
  }
}
