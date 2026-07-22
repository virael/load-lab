package com.loadlab.worker;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class RunExecutorService {

  private final Map<String, RunResult> store = new ConcurrentHashMap<>();
  private final Map<String, RunMetrics> liveMetrics = new ConcurrentHashMap<>();
  private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

  private final ExecutorService orchestrator = Executors.newCachedThreadPool();
  private final WebClient webClient;
  private final MetricsPublisher metricsPublisher;

  public RunExecutorService(MetricsPublisher metricsPublisher, WebClient webClient) {
    this.metricsPublisher = metricsPublisher;
    this.webClient = webClient;
  }

  // Used by the direct REST entrypoint (POST /runs), kept for standalone
  // debugging — generates its own id. The live, Kafka-driven flow uses the
  // overload below with a controller-issued id instead. No Kafka message backs a
  // REST call, so there is nothing to acknowledge.
  public RunResult startRun(RunRequest req) {
    return startRun(UUID.randomUUID().toString(), req, 0, null);
  }

  // Backwards-compatible overload for callers that have no Kafka acknowledgement
  // to hand back (the REST path and the tests). Delegates with a null ack.
  public RunResult startRun(String id, RunRequest req, long rampDelayMs) {
    return startRun(id, req, rampDelayMs, null);
  }

  public RunResult startRun(String id, RunRequest req, long rampDelayMs, Acknowledgment ack) {
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
          // Acknowledge to Kafka ONLY now — after the traffic generation has
          // actually finished, not on mere command receipt. This keeps the
          // consumer lag that KEDA autoscales on reflecting real in-flight work.
          // ack is null on the REST/test path, where there is nothing to commit.
          if (ack != null) ack.acknowledge();
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

    // The engine: instead of virtualUsers OS threads each blocking on http.send(),
    // a single Flux keeps virtualUsers requests in flight over a small event-loop
    // pool. flatMap's concurrency argument is what enforces "N virtual users".
    Flux.range(0, Integer.MAX_VALUE)
        .flatMap(i -> makeRequest(req.targetUrl(), metrics), req.virtualUsers())
        .takeUntilOther(Mono.delay(Duration.ofSeconds(req.durationSeconds())))
        .then()
        // Safe: this runs on an `orchestrator` thread (one per test run), never on an
        // event-loop or Kafka-listener thread. It is the deliberate seam between the
        // blocking world (orchestrating one test) and the non-blocking one (traffic).
        .block();

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

  private Mono<Void> makeRequest(String targetUrl, RunMetrics metrics) {
    long start = System.nanoTime();
    return webClient
        .get()
        .uri(targetUrl)
        .exchangeToMono(
            response -> {
              boolean isError = response.statusCode().isError();
              // Mandatory with exchangeToMono: unlike retrieve(), it hands you the
              // response lifecycle. Skipping this slowly leaks pooled connections
              // under load until the pool is exhausted.
              return response.releaseBody().thenReturn(isError);
            })
        .doOnNext(
            isError -> {
              long latencyMs = (System.nanoTime() - start) / 1_000_000;
              metrics.recordRequest(latencyMs, isError);
            })
        .onErrorResume(
            e -> {
              // CRITICAL: an error from ONE request must not terminate the whole
              // flatMap stream. Turn the error into an ordinary value (counted as an
              // error in the metrics) and keep going, exactly as the old per-thread
              // try/catch did for a single VU.
              long latencyMs = (System.nanoTime() - start) / 1_000_000;
              metrics.recordRequest(latencyMs, true);
              return Mono.empty();
            })
        .then();
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
        snap.p99Ms(),
        snap.histogramBytes());
  }

  private RunResult emptyResult(String id, String status) {
    return new RunResult(id, status, 0, 0.0, 0, 0, 0, 0, null);
  }
}
