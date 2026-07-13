package com.loadlab.worker;

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
public class RunExecutorService {

  private final Map<String, RunResult> store = new ConcurrentHashMap<>();
  private final Map<String, RunMetrics> liveMetrics = new ConcurrentHashMap<>();
  private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

  private final ExecutorService orchestrator = Executors.newCachedThreadPool();
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final MetricsPublisher metricsPublisher;

  public RunExecutorService(MetricsPublisher metricsPublisher) {
    this.metricsPublisher = metricsPublisher;
  }

  // Used by the direct REST entrypoint (POST /runs), kept for standalone
  // debugging — generates its own id. The live, Kafka-driven flow uses the
  // overload below with a controller-issued id instead.
  public RunResult startRun(RunRequest req) {
    return startRun(UUID.randomUUID().toString(), req, 0);
  }

  public RunResult startRun(String id, RunRequest req, long rampDelayMs) {
    store.put(id, emptyResult(id, "PENDING"));
    liveMetrics.put(id, new RunMetrics());
    emitters.put(id, new CopyOnWriteArrayList<>());
    // The ramp delay is deliberately inside the async task, not before it, so the
    // Kafka listener thread returns immediately to poll for the next command
    // instead of blocking on a sleep.
    orchestrator.submit(
        () -> {
          sleepQuietly(rampDelayMs);
          runLoad(id, req);
        });
    return store.get(id);
  }

  private void sleepQuietly(long ms) {
    if (ms <= 0) return;
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public RunResult getResult(String id) {
    RunMetrics metrics = liveMetrics.get(id);
    return metrics != null ? toResult(id, "RUNNING", metrics) : store.get(id);
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
      RunMetrics metrics = liveMetrics.get(id);
      if (metrics == null) continue;

      RunResult snapshot = toResult(id, "RUNNING", metrics);
      metricsPublisher.publish(snapshot);

      var list = emitters.get(id);
      if (list == null || list.isEmpty()) continue;
      for (SseEmitter emitter : list) {
        try {
          emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (IOException e) {
          list.remove(emitter);
        }
      }
    }
  }

  private void runLoad(String id, RunRequest req) {
    RunMetrics metrics = liveMetrics.get(id);
    store.put(id, emptyResult(id, "RUNNING"));

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
                    long latencyMs = (System.nanoTime() - start) / 1_000_000;
                    metrics.recordRequest(latencyMs, isError);
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

    RunResult finalResult = toResult(id, "DONE", metrics);

    // Leave liveMetrics BEFORE publishing DONE: otherwise the 1s scheduler can
    // still emit a RUNNING snapshot that lands after it and resurrects a
    // finished test. This narrows the window rather than closing it, so the
    // controller also refuses to overwrite a terminal status.
    liveMetrics.remove(id);
    store.put(id, finalResult);
    metricsPublisher.publish(finalResult);

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
    emitters.remove(id);
  }

  private RunResult toResult(String id, String status, RunMetrics metrics) {
    RunMetrics.Snapshot snap = metrics.snapshot();
    return new RunResult(
        id,
        status,
        metrics.totalRequests(),
        snap.avgMs(),
        metrics.errors(),
        snap.p50Ms(),
        snap.p95Ms(),
        snap.p99Ms());
  }

  private RunResult emptyResult(String id, String status) {
    return new RunResult(id, status, 0, 0.0, 0, 0, 0, 0);
  }
}
