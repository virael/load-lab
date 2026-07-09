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

  private final Map<String, TestResult> store = new ConcurrentHashMap<>();
  private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
  private final ExecutorService orchestrator = Executors.newCachedThreadPool();
  private final WorkerClient workerClient;

  public LoadTestService(WorkerClient workerClient) {
    this.workerClient = workerClient;
  }

  public TestResult startTest(TestRequest req) {
    TestResult initial;
    try {
      initial = workerClient.startRun(req);
    } catch (Exception e) {
      throw new IllegalStateException("Worker unreachable", e);
    }

    String id = initial.id();
    store.put(id, initial);
    emitters.put(id, new CopyOnWriteArrayList<>());

    orchestrator.submit(() -> relayStream(id));
    return initial;
  }

  public TestResult getResult(String id) {
    return store.get(id);
  }

  public SseEmitter subscribe(String id) {
    SseEmitter emitter = new SseEmitter(0L);
    var list = emitters.get(id);
    if (list == null) {
      // Either an unknown id, or the run already finished streaming.
      // Replay the final result once so a late subscriber still sees
      // the outcome instead of a silently empty stream.
      TestResult last = store.get(id);
      if (last != null) {
        try {
          emitter.send(SseEmitter.event().name("snapshot").data(last));
        } catch (IOException ignored) {
          // Client already gone — nothing to do.
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
            var list = emitters.get(id);
            if (list == null) return;
            for (SseEmitter emitter : list) {
              try {
                emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
                if ("DONE".equals(snapshot.status())) emitter.complete();
              } catch (IOException e) {
                list.remove(emitter);
              }
            }
          });
    } catch (Exception e) {
      var list = emitters.get(id);
      if (list != null) list.forEach(emitter -> emitter.completeWithError(e));
    } finally {
      emitters.remove(id);
    }
  }
}
