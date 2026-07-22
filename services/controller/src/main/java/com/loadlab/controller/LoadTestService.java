package com.loadlab.controller;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LoadTestService {

  private static final Logger log = LoggerFactory.getLogger(LoadTestService.class);

  private static final long RAMP_STEP_MS = 300;

  // A worker reports every second (E4.2), so 15s of silence is well past jitter and
  // GC pauses — it means the process is gone, not slow.
  private static final Duration STUCK_THRESHOLD = Duration.ofSeconds(15);
  private static final int MAX_RETRIES = 2;

  private final Map<String, TestResult> store = new ConcurrentHashMap<>();
  private final Map<String, CopyOnWriteArrayList<ConflatingRelay<TestResult>>> emitters =
      new ConcurrentHashMap<>();
  private final Map<String, String> subIdToTestId = new ConcurrentHashMap<>();
  private final Map<String, Set<String>> testSubIds = new ConcurrentHashMap<>();
  private final Map<String, TestResult> subResults = new ConcurrentHashMap<>();

  // Idempotency guard: once a sub-run reaches a terminal state (DONE/FAILED/PARTIAL),
  // any further message for the same subId is ignored. Kafka redelivery (a worker
  // rebalance after the worker's manual-ack change) combined with this controller's
  // own watchdog redispatch can each independently cause more than one worker to
  // complete the same subId. Accepting only the FIRST terminal result prevents a
  // late or duplicate completion from silently corrupting an already-finalized,
  // already-displayed test result. Grows unboundedly for the process lifetime — the
  // same accepted trade-off as store/subResults/testSubIds above.
  private final Set<String> terminalSubIds = ConcurrentHashMap.newKeySet();

  // Watchdog state. Kafka commits the command's offset milliseconds after delivery,
  // long before the run it started actually finishes, so a partition rebalance cannot
  // recover work a worker had in flight when it died. Only the controller can notice
  // the resulting silence, so it tracks liveness per sub-run here.
  private final Map<String, Instant> subIdLastActivity = new ConcurrentHashMap<>();
  private final Map<String, Integer> subIdRetryCount = new ConcurrentHashMap<>();
  private final Map<String, RunCommand> subIdOriginalCommand = new ConcurrentHashMap<>();
  // When each sub-run first started, so a redispatch after a worker death can subtract
  // the time already spent instead of replaying the whole original window from zero.
  private final Map<String, Instant> subIdStartedAt = new ConcurrentHashMap<>();

  private final CommandPublisher commandPublisher;
  private final WorkerClient workerClient;
  private final ResultPublisher resultPublisher;
  private final TestRepository testRepository;
  private final int workerCapacityVus;
  private final int maxWorkersPerTest;

  public LoadTestService(
      CommandPublisher commandPublisher,
      WorkerClient workerClient,
      ResultPublisher resultPublisher,
      TestRepository testRepository,
      @Value("${loadtest.worker-capacity-vus:5000}") int workerCapacityVus,
      @Value("${loadtest.max-workers-per-test:20}") int maxWorkersPerTest) {
    this.commandPublisher = commandPublisher;
    this.workerClient = workerClient;
    this.resultPublisher = resultPublisher;
    this.testRepository = testRepository;
    this.workerCapacityVus = workerCapacityVus;
    this.maxWorkersPerTest = maxWorkersPerTest;
  }

  public TestResult startTest(TestRequest req) {
    // Best-effort pre-flight only. Publishing is fire-and-forget: a successful send
    // means the broker accepted the message, NOT that any worker will run it.
    if (!workerClient.isHealthy()) {
      throw new WorkerUnavailableException("No healthy worker found before publishing command");
    }

    String testId = UUID.randomUUID().toString();
    TestResult initial = emptyResult(testId, "PENDING");
    store.put(testId, initial);
    // Durable record of who/what/when, separate from the in-memory hot path.
    testRepository.insertPending(initial, req);
    emitters.put(testId, new CopyOnWriteArrayList<>());

    // Sub-run count now follows the requested load instead of being pinned at 3. Each
    // published command is one unit of demand; the resulting backlog on test-commands
    // is what the platform scales on.
    int workerCount =
        LoadSplitter.computeWorkerCount(req.virtualUsers(), workerCapacityVus, maxWorkersPerTest);
    int[] shares = LoadSplitter.computeShares(req.virtualUsers(), workerCount);
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
        String subId = testId + "-" + i;
        RunCommand command =
            new RunCommand(
                subId, req.targetUrl(), shares[i], req.durationSeconds(), i * RAMP_STEP_MS);
        commandPublisher.publish(command);
        // Keep the exact command around: a redispatch has to be byte-identical,
        // including the same subId, or the result would not route back to this test.
        subIdLastActivity.put(subId, Instant.now());
        subIdRetryCount.put(subId, 0);
        subIdOriginalCommand.put(subId, command);
        subIdStartedAt.put(subId, Instant.now());
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
    // The actual send() — the blocking network write to this one browser — now happens
    // inside this lambda, which runs on the relay's own virtual thread. The Kafka
    // listener thread never touches the socket.
    ConflatingRelay<TestResult> relay =
        new ConflatingRelay<>(
            result -> {
              try {
                emitter.send(SseEmitter.event().name("snapshot").data(result));
                if (isTerminal(result.status())) emitter.complete();
              } catch (IOException e) {
                emitter.completeWithError(e);
              }
            });

    list.add(relay);
    Runnable cleanup =
        () -> {
          list.remove(relay);
          relay.close();
        };
    emitter.onCompletion(cleanup);
    emitter.onTimeout(cleanup);
    // onError too, not just the two the happy path uses: a client vanishing mid-write
    // is the single most likely way this relay ever ends, and without it the virtual
    // thread would linger for the life of the process.
    emitter.onError(e -> cleanup.run());
    return emitter;
  }

  @KafkaListener(topics = "test-metrics", groupId = "controller-group")
  public void onMetrics(TestResult subResult) {
    String subId = subResult.id();

    // Already resolved — ignore any duplicate or late message for this subId. This is
    // what stops a second worker's completion (from Kafka redelivery + watchdog
    // redispatch of the same subId) from overwriting an already-finalized result.
    if (terminalSubIds.contains(subId)) return;

    String testId = subIdToTestId.get(subId);
    if (testId == null) return; // stale, unknown, or already-finished sub-run

    // Proof of life, refreshed on every snapshot. Deliberately AFTER the routing
    // check: re-arming the watchdog for a sub-run that was already cleaned up would
    // make a late straggler trigger a redispatch of a test that finished long ago.
    subIdLastActivity.put(subId, Instant.now());

    TestResult previousSub = subResults.get(subId);
    if (previousSub != null && isTerminal(previousSub.status())) {
      // The worker's 1s scheduler can race its own DONE. Never let a stale RUNNING
      // snapshot un-finish a sub-run that already reported DONE.
      return;
    }

    // Atomic claim: only the thread that WINS add() (returns true) proceeds to the
    // write/broadcast/publish below. A concurrent second terminal message for the same
    // subId — or a concurrent watchdog decision — safely no-ops here. Set.add() closes
    // the check-then-act window the previous (contains()-then-add()) version left open.
    if (isTerminal(subResult.status())) {
      if (!terminalSubIds.add(subId)) {
        return;
      }
    }

    subResults.put(subId, subResult);
    TestResult merged = mergeSubResults(testId);
    if (merged == null) return;

    finalizeMerged(testId, merged);
  }

  // A worker cannot detect its own death — that is what a crash is. So the controller
  // watches for the silence instead, and republishes the command that went quiet.
  @Scheduled(fixedRate = 5000)
  void detectStuckSubRuns() {
    Instant now = Instant.now();
    for (var entry : subIdLastActivity.entrySet()) {
      String subId = entry.getKey();
      TestResult current = subResults.get(subId);
      if (current != null && isTerminal(current.status())) continue;

      if (Duration.between(entry.getValue(), now).compareTo(STUCK_THRESHOLD) > 0) {
        handleStuckSubRun(subId);
      }
    }
  }

  // Package-private (not private) so a same-package test can drive it directly without
  // an artificial hook. Called only from detectStuckSubRuns() in production.
  void handleStuckSubRun(String subId) {
    // Resolved by a real completion from Kafka in the meantime — do not let the
    // watchdog overwrite that terminal result with a FAILED, nor redispatch a sub-run
    // that already finished. Symmetric with the guard at the top of onMetrics().
    if (terminalSubIds.contains(subId)) {
      subIdLastActivity.remove(subId);
      return;
    }

    String testId = subIdToTestId.get(subId);
    if (testId == null) return; // test already finished and was cleaned up

    int retries = subIdRetryCount.getOrDefault(subId, 0);
    if (retries < MAX_RETRIES) {
      RunCommand original = subIdOriginalCommand.get(subId);
      if (original == null) return;

      // A redispatch must NOT just replay the original command: the worker that picks it
      // up would run a full, fresh time window from zero even though part of the work
      // already ran before the previous worker died — inflating both the wall-clock
      // recovery time and the reported request count. Subtract the elapsed time, with a
      // 1s floor to avoid a degenerate zero/negative window. rampDelayMs is forced to 0:
      // the ramp made sense at test start (staggering many workers), not during failure
      // recovery, where the work should resume immediately.
      Instant startedAt = subIdStartedAt.getOrDefault(subId, Instant.now());
      long elapsedSeconds = Duration.between(startedAt, Instant.now()).getSeconds();
      long remainingSeconds = Math.max(1, original.durationSeconds() - elapsedSeconds);
      RunCommand adjusted =
          new RunCommand(
              subId, original.targetUrl(), original.virtualUsers(), (int) remainingSeconds, 0L);

      subIdRetryCount.put(subId, retries + 1);
      // Reset the clock now, not when work resumes: the redispatched run needs a full
      // threshold to report in before we consider it dead again.
      subIdLastActivity.put(subId, Instant.now());
      // Same subId on purpose — it is already registered in subIdToTestId/testSubIds,
      // so whichever live worker picks this up routes back to the right test. If the
      // "dead" worker turns out to be merely slow and finishes too, its snapshot just
      // overwrites the same map entry. Newest wins; no request is counted twice.
      log.warn(
          "Sub-run {} silent for over {}s, redispatching (attempt {}/{}), {}s remaining",
          subId,
          STUCK_THRESHOLD.toSeconds(),
          retries + 1,
          MAX_RETRIES,
          remainingSeconds);
      commandPublisher.publish(adjusted);
      return;
    }

    // Out of retries. Marking this slice FAILED is the honest option: it lets the
    // merge finish as PARTIAL instead of hanging in RUNNING forever, or — worse —
    // reporting DONE for work that never ran. Atomically claim FAILED as terminal; if
    // a real completion won the race first, back off rather than overwrite it.
    if (!terminalSubIds.add(subId)) {
      subIdLastActivity.remove(subId);
      return;
    }
    log.error(
        "Sub-run {} still silent after {} redispatches, marking FAILED — test {} can only be PARTIAL",
        subId,
        MAX_RETRIES,
        testId);
    subResults.put(subId, new TestResult(subId, "FAILED", 0, 0.0, 0, 0, 0, 0, null));
    subIdLastActivity.remove(subId);

    TestResult merged = mergeSubResults(testId);
    if (merged == null) return;
    // Same tail as onMetrics: a PARTIAL is a real outcome and has to reach the
    // aggregator and the durable row, not just the SSE stream.
    finalizeMerged(testId, merged);
  }

  // Shared finalize tail for both onMetrics and the watchdog's FAILED path: push the
  // merged result to the in-memory store, the SSE relay, the aggregator pipeline and the
  // durable row, then drop routing state once the whole test is terminal.
  private void finalizeMerged(String testId, TestResult merged) {
    store.put(testId, merged);
    broadcast(testId, merged);
    // Hand the already-merged result to the aggregator's pipeline. Published on every
    // snapshot (RUNNING included) so it can compute per-window deltas, not just the
    // final total.
    resultPublisher.publish(merged);
    // Persist the latest merged state onto the same row (UPDATE by id), keeping the
    // in-memory store as the hot read path for getResult()/SSE.
    testRepository.updateProgress(merged);

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
    boolean anyFailed = false;
    boolean allTerminal = true;
    List<byte[]> histograms = new ArrayList<>();

    for (String subId : subIds) {
      TestResult sub = subResults.get(subId);
      if (sub == null) {
        allTerminal = false;
        continue;
      }
      if ("FAILED".equals(sub.status())) {
        anyFailed = true;
        continue; // contributes nothing: that slice of work genuinely never happened
      }
      if (!"DONE".equals(sub.status())) allTerminal = false;
      totalRequests += sub.totalRequests();
      errors += sub.errors();
      histograms.add(sub.histogram());
    }

    var merged = HistogramMerger.merge(histograms);
    // Three states, not two. PARTIAL says "finished, but some of the load never ran" —
    // neither a lie (DONE) nor a hang (RUNNING).
    String status = !allTerminal ? "RUNNING" : anyFailed ? "PARTIAL" : "DONE";
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
      // The watchdog maps must be cleared here too. Leaving them behind is not just a
      // leak: detectStuckSubRuns judges liveness against subResults, which this method
      // just emptied, so a surviving entry looks permanently silent and would
      // redispatch the command of a test that already finished.
      subIdLastActivity.remove(subId);
      subIdRetryCount.remove(subId);
      subIdOriginalCommand.remove(subId);
    }
  }

  private void broadcast(String id, TestResult result) {
    var list = emitters.get(id);
    if (list == null) return;
    for (ConflatingRelay<TestResult> relay : list) {
      // Hand off and move on. This is the whole point: a wedged browser can no longer
      // stall the listener thread that is serving every other test in the system.
      relay.offer(result);
    }
    if (isTerminal(result.status())) {
      // No more snapshots are coming. Drop the entry so a late subscriber falls into
      // the replay branch of subscribe() instead of hanging on a dead stream.
      emitters.remove(id);
    }
  }

  private boolean isTerminal(String status) {
    return "DONE".equals(status) || "FAILED".equals(status) || "PARTIAL".equals(status);
  }

  private TestResult emptyResult(String id, String status) {
    return new TestResult(id, status, 0, 0.0, 0, 0, 0, 0, null);
  }
}
