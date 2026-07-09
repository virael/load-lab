package com.loadlab.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LoadTestService {

  private static final int MAX_START_ATTEMPTS = 3;
  private static final long INITIAL_BACKOFF_MS = 200;

  private final Map<String, TestResult> store = new ConcurrentHashMap<>();
  private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
  private final ExecutorService orchestrator = Executors.newCachedThreadPool();
  private final WorkerClient workerClient;

  public LoadTestService(WorkerClient workerClient) {
    this.workerClient = workerClient;
  }

  public TestResult startTest(TestRequest req) {
    // Fast-fail for the common case (worker never started), without waiting
    // through a full retry cycle. NOTE: this is a best-effort check, not a
    // guarantee — the worker could still fail between this check and the
    // actual call below (a classic check-then-act race in distributed systems).
    if (!workerClient.isHealthy()) {
      throw new WorkerUnavailableException("Worker health check failed before starting test");
    }

    TestResult initial = startRunWithRetry(req);
    String id = initial.id(); // reuse the worker's id — single-worker setup for now
    store.put(id, initial);
    emitters.put(id, new CopyOnWriteArrayList<>());

    orchestrator.submit(() -> relayStream(id));
    return initial;
  }

  private TestResult startRunWithRetry(TestRequest req) {
    long backoffMs = INITIAL_BACKOFF_MS;
    Exception lastError = null;

    for (int attempt = 1; attempt <= MAX_START_ATTEMPTS; attempt++) {
      try {
        return workerClient.startRun(req);
      } catch (Exception e) {
        lastError = e;
        if (attempt < MAX_START_ATTEMPTS) {
          sleepQuietly(backoffMs);
          backoffMs *= 2; // exponential backoff: 200ms, 400ms
        }
      }
    }
    throw new WorkerUnavailableException(
        "Worker did not accept the run after " + MAX_START_ATTEMPTS + " attempts", lastError);
  }

  private void sleepQuietly(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public TestResult getResult(String id) {
    return store.get(id);
  }

  public SseEmitter subscribe(String id) {
    SseEmitter emitter = new SseEmitter(0L);
    var list = emitters.get(id);
    if (list == null) {
      // Unknown id, or the run already finished streaming — replay the
      // final result once so a late subscriber sees the outcome instead
      // of a silently empty stream.
      TestResult last = store.get(id);
      if (last != null) {
        try {
          emitter.send(SseEmitter.event().name("snapshot").data(last));
        } catch (IOException ignored) {
        }
      }
      emitter.complete();
      return emitter;
    }
    list.add(emitter);
    emitter.onCompletion(() -> list.remove(emitter));
    emitter.onTimeout(() -> list.remove(emitter));
    return emitter;
  }

  private void relayStream(String id) {
    try {
      workerClient.streamRun(
          id,
          snapshot -> {
            store.put(id, snapshot);
            broadcast(id, snapshot);
          });
    } catch (Exception e) {
      // The worker connection dropped mid-stream. We deliberately do NOT
      // attempt to reconnect here — that would need to reconcile a gap in
      // history and is a bigger problem (Phase 6: resilience/backpressure).
      // For now: abort cleanly, surfacing whatever partial data we last saw.
      TestResult last = store.get(id);
      TestResult failed =
          new TestResult(
              id,
              "FAILED",
              last != null ? last.totalRequests() : 0,
              last != null ? last.avgLatencyMs() : 0.0,
              last != null ? last.errors() : 0,
              last != null ? last.p50Ms() : 0,
              last != null ? last.p95Ms() : 0,
              last != null ? last.p99Ms() : 0);
      store.put(id, failed);
      broadcast(id, failed);
    } finally {
      emitters.remove(id);
    }
  }

  private void broadcast(String id, TestResult result) {
    var list = emitters.get(id);
    if (list == null) return;
    for (SseEmitter emitter : list) {
      try {
        emitter.send(SseEmitter.event().name("snapshot").data(result));
        if (isTerminal(result.status())) emitter.complete();
      } catch (IOException e) {
        list.remove(emitter);
      }
    }
  }

  private boolean isTerminal(String status) {
    return "DONE".equals(status) || "FAILED".equals(status);
  }
}
