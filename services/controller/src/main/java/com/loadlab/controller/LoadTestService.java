package com.loadlab.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LoadTestService {

  private static final int WORKERS_PER_TEST = 3;
  private static final long RAMP_STEP_MS = 300;

  private final Map<String, TestResult> store = new ConcurrentHashMap<>();
  private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
  private final Map<String, String> subIdToTestId = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> testSubIds = new ConcurrentHashMap<>();
  private final Map<String, TestResult> subResults = new ConcurrentHashMap<>();

  private final CommandPublisher commandPublisher;
  private final WorkerClient workerClient;

  public LoadTestService(CommandPublisher commandPublisher, WorkerClient workerClient) {
    this.commandPublisher = commandPublisher;
    this.workerClient = workerClient;
  }

  public TestResult startTest(TestRequest req) {
    // Best-effort pre-flight only. Publishing is fire-and-forget: a successful send
    // means the broker accepted the message, NOT that any worker will run it.
    if (!workerClient.isHealthy()) {
      throw new WorkerUnavailableException("No healthy worker found before publishing command");
    }

    String testId = UUID.randomUUID().toString();
    store.put(testId, emptyResult(testId, "PENDING"));
    emitters.put(testId, new CopyOnWriteArrayList<>());

    int[] shares = LoadSplitter.computeShares(req.virtualUsers(), WORKERS_PER_TEST);
    Set<String> subIds = ConcurrentHashMap.newKeySet();
    for (int i = 0; i < shares.length; i++) {
      if (shares[i] == 0) continue;
      subIds.add(testId + "-" + i);
    }

    // Register the routing tables BEFORE publishing. A worker can consume a command
    // and publish metrics for it before publish() even returns, and onMetrics needs
    // both maps populated or it drops the message as unknown.
    subIds.forEach(subId -> subIdToTestId.put(subId, testId));
    testSubIds.put(testId, subIds);

    try {
      for (int i = 0; i < shares.length; i++) {
        if (shares[i] == 0) continue;
        commandPublisher.publish(
            new RunCommand(
                testId + "-" + i,
                req.targetUrl(),
                shares[i],
                req.durationSeconds(),
                i * RAMP_STEP_MS));
      }
    } catch (Exception e) {
      cleanupRouting(testId);
      throw new WorkerUnavailableException("Could not publish run commands to Kafka", e);
    }

    return store.get(testId);
  }

  public TestResult getResult(String id) {
    return store.get(id);
  }

  public SseEmitter subscribe(String id) {
    SseEmitter emitter = new SseEmitter(0L);
    var list = emitters.get(id);
    if (list == null) {
      // Unknown id, or the run already finished streaming — replay the final result
      // once so a late subscriber sees the outcome, not an empty stream.
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

  @KafkaListener(topics = "test-metrics", groupId = "controller-group")
  public void onMetrics(TestResult subResult) {
    String testId = subIdToTestId.get(subResult.id());
    if (testId == null) return; // stale, unknown, or already-finished sub-run

    TestResult previousSub = subResults.get(subResult.id());
    if (previousSub != null && isTerminal(previousSub.status())) {
      // The worker's 1s scheduler can race its own DONE. Never let a stale RUNNING
      // snapshot un-finish a sub-run that already reported DONE.
      return;
    }

    subResults.put(subResult.id(), subResult);
    TestResult merged = mergeSubResults(testId);
    if (merged == null) return;

    store.put(testId, merged);
    broadcast(testId, merged);

    if (isTerminal(merged.status())) {
      cleanupRouting(testId);
    }
  }

  // Sums stay sums (always correct). Percentiles are now computed from the MERGED
  // histograms rather than from the workers' pre-computed numbers — the max-of-p99
  // stand-in from E4.3 is gone.
  private TestResult mergeSubResults(String testId) {
    Set<String> subIds = testSubIds.get(testId);
    if (subIds == null) return null; // test already completed and was cleaned up

    long totalRequests = 0;
    long errors = 0;
    boolean allDone = true;
    List<byte[]> histograms = new ArrayList<>();

    for (String subId : subIds) {
      TestResult sub = subResults.get(subId);
      if (sub == null) {
        allDone = false;
        continue;
      }
      totalRequests += sub.totalRequests();
      errors += sub.errors();
      if (!"DONE".equals(sub.status())) allDone = false;
      histograms.add(sub.histogram());
    }

    var merged = HistogramMerger.merge(histograms);
    String status = allDone ? "DONE" : "RUNNING";
    // Null histogram on the way out: the frontend needs the numbers, not the raw
    // distribution, and @JsonInclude(NON_NULL) keeps the field out of the payload.
    return new TestResult(
        testId,
        status,
        totalRequests,
        merged.avgMs(),
        errors,
        merged.p50Ms(),
        merged.p95Ms(),
        merged.p99Ms(),
        null);
  }

  // Drop the per-test routing state once the test is terminal. Without this, every
  // test ever run leaks three map entries per sub-run for the life of the process.
  // It also makes any late straggler snapshot a no-op: its subId no longer resolves.
  private void cleanupRouting(String testId) {
    Set<String> subIds = testSubIds.remove(testId);
    if (subIds == null) return;
    for (String subId : subIds) {
      subIdToTestId.remove(subId);
      subResults.remove(subId);
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
    if (isTerminal(result.status())) {
      // No more snapshots are coming. Drop the entry so a late subscriber falls into
      // the replay branch of subscribe() instead of hanging on a dead stream.
      emitters.remove(id);
    }
  }

  private boolean isTerminal(String status) {
    return "DONE".equals(status) || "FAILED".equals(status);
  }

  private TestResult emptyResult(String id, String status) {
    return new TestResult(id, status, 0, 0.0, 0, 0, 0, 0, null);
  }
}
