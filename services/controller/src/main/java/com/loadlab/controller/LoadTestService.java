package com.loadlab.controller;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LoadTestService {

  private final Map<String, TestResult> store = new ConcurrentHashMap<>();
  private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
  private final CommandPublisher commandPublisher;
  private final WorkerClient workerClient;

  public LoadTestService(CommandPublisher commandPublisher, WorkerClient workerClient) {
    this.commandPublisher = commandPublisher;
    this.workerClient = workerClient;
  }

  public TestResult startTest(TestRequest req) {
    // Best-effort pre-flight only. Publishing to Kafka is fire-and-forget: a
    // successful send means the broker accepted the message, NOT that any worker
    // will process it. A worker that dies after picking up the command leaves the
    // test PENDING forever with no error — the price of decoupling, to be handled
    // by a timeout in Phase 6.
    if (!workerClient.isHealthy()) {
      throw new WorkerUnavailableException("No healthy worker found before publishing command");
    }

    String id = UUID.randomUUID().toString();
    TestResult initial = emptyResult(id, "PENDING");
    store.put(id, initial);
    emitters.put(id, new CopyOnWriteArrayList<>());

    try {
      commandPublisher.publish(
          new RunCommand(id, req.targetUrl(), req.virtualUsers(), req.durationSeconds()));
    } catch (Exception e) {
      throw new WorkerUnavailableException("Could not publish run command to Kafka", e);
    }

    return initial;
  }

  public TestResult getResult(String id) {
    return store.get(id);
  }

  public SseEmitter subscribe(String id) {
    SseEmitter emitter = new SseEmitter(0L);
    var list = emitters.get(id);
    if (list == null) {
      // Unknown id, or the run already finished streaming — replay the final
      // result once so a late subscriber sees the outcome, not an empty stream.
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

  // A single long-lived listener serves every in-flight test, routing by the id in
  // the message. This replaces the per-test relay thread the REST/SSE version needed.
  @KafkaListener(topics = "test-metrics", groupId = "controller-group")
  public void onMetrics(TestResult result) {
    TestResult previous = store.get(result.id());
    if (previous != null && isTerminal(previous.status())) {
      // The worker's 1s snapshot scheduler can race its own DONE message. Once a
      // test is terminal, a stale RUNNING snapshot must not resurrect it.
      return;
    }
    store.put(result.id(), result);
    broadcast(result.id(), result);
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
      // No further snapshots are coming. Drop the entry so a late subscriber falls
      // into the replay branch of subscribe() instead of hanging on a dead stream —
      // and so finished tests stop accumulating emitter lists forever.
      emitters.remove(id);
    }
  }

  private boolean isTerminal(String status) {
    return "DONE".equals(status) || "FAILED".equals(status);
  }

  private TestResult emptyResult(String id, String status) {
    return new TestResult(id, status, 0, 0.0, 0, 0, 0, 0);
  }
}
