package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

// Plain unit test of LoadTestService: no Spring context, no Kafka, no clock to wait on.
// Two behaviours that until now had only manual (docker-kill) or effect-only integration
// coverage are exercised directly here:
//   1. the stuck-sub-run watchdog, driven through retries to exhaustion, and
//   2. the late-subscriber replay branch of subscribe().
class LoadTestServiceTest {

  // Mirrors the private constant in LoadTestService — the number of redispatches before a
  // silent sub-run is given up on. Kept in sync by hand; if the production value changes,
  // marksSubRunFailedAfterExhaustingRetries fails loudly rather than silently drifting.
  private static final int MAX_RETRIES = 2;

  private CommandPublisher commandPublisher;
  private WorkerClient workerClient;
  private ResultPublisher resultPublisher;
  private TestRepository testRepository;
  private LoadTestService service;

  @BeforeEach
  void setUp() {
    commandPublisher = mock(CommandPublisher.class);
    workerClient = mock(WorkerClient.class);
    resultPublisher = mock(ResultPublisher.class);
    testRepository = mock(TestRepository.class);
    // Pre-flight health check must pass or startTest short-circuits before publishing.
    when(workerClient.isHealthy()).thenReturn(true);
    service =
        new LoadTestService(
            commandPublisher, workerClient, resultPublisher, testRepository, 5000, 20);
  }

  // --- Watchdog: detectStuckSubRuns() -> handleStuckSubRun() ---

  @Test
  void republishesTheOriginalCommandWhenASubRunGoesSilent() {
    // 1 VU -> exactly one sub-run, one initial dispatch.
    String testId = service.startTest(new TestRequest("http://localhost/ping", 1, 1)).id();
    String subId = testId + "-0";
    RunCommand original = captureSinglePublishedCommand();

    // One silent window (past the 15s threshold) is enough to trigger the first retry.
    backdateLastActivity(subId, Duration.ofSeconds(16));
    service.detectStuckSubRuns();

    // The SAME command (same subId) is republished — a redispatch has to be byte-identical
    // or the worker's result would not route back to this test.
    verify(commandPublisher, times(2)).publish(original);
    // Still recoverable: one retry does not mark the slice FAILED.
    assertThat(service.getResult(testId).status()).isNotEqualTo("PARTIAL");
  }

  @Test
  void marksSubRunFailedAfterExhaustingRetries() {
    String testId = service.startTest(new TestRequest("http://localhost/ping", 1, 1)).id();
    String subId = testId + "-0";

    // MAX_RETRIES redispatches, then one more silent window with no retries left.
    for (int i = 0; i < MAX_RETRIES + 1; i++) {
      backdateLastActivity(subId, Duration.ofSeconds(16));
      service.detectStuckSubRuns();
    }

    // The whole test settles as PARTIAL — the honest outcome for load that never ran —
    // instead of hanging in RUNNING forever.
    assertThat(service.getResult(testId).status()).isEqualTo("PARTIAL");
    // Exactly one initial dispatch plus MAX_RETRIES redispatches, and not one more.
    verify(commandPublisher, times(1 + MAX_RETRIES)).publish(any(RunCommand.class));
    // A PARTIAL is a real result: it must reach the aggregator pipeline and the durable
    // row, not only the SSE stream.
    verify(resultPublisher).publish(argThat(r -> "PARTIAL".equals(r.status())));
    verify(testRepository).updateProgress(argThat(r -> "PARTIAL".equals(r.status())));

    // And it is fully cleaned up: a further silent window neither resurrects it nor
    // republishes anything — the routing/watchdog state for this sub-run is gone.
    backdateLastActivity(subId, Duration.ofSeconds(16));
    service.detectStuckSubRuns();
    verify(commandPublisher, times(1 + MAX_RETRIES)).publish(any(RunCommand.class));
  }

  // --- subscribe(): the late-subscriber replay branch (emitters gone, store still holds
  // the final result) ---

  @Test
  void replaysTheFinalResultToASubscriberThatArrivesAfterTheStreamClosed() throws Exception {
    // No emitters entry for this id (the stream already finished and was dropped), but the
    // final result is still in the store — the exact state a late /stream request hits.
    TestResult finished = new TestResult("late-1", "DONE", 500, 12.0, 0, 8, 9, 10, null);
    putInStore("late-1", finished);

    SseEmitter emitter = service.subscribe("late-1");
    Captured captured = drive(emitter);

    // The late subscriber gets the outcome replayed once, then the stream is completed —
    // not an empty, hanging stream.
    assertThat(captured.payloads()).containsExactly(finished);
    assertThat(captured.completed()).isTrue();
  }

  @Test
  void completesWithoutReplayForAnUnknownId() throws Exception {
    // Nothing in emitters and nothing in store: an unknown id must close cleanly with no
    // snapshot rather than sending a bogus one.
    SseEmitter emitter = service.subscribe("does-not-exist");
    Captured captured = drive(emitter);

    assertThat(captured.payloads()).isEmpty();
    assertThat(captured.completed()).isTrue();
  }

  // --- helpers ---

  private RunCommand captureSinglePublishedCommand() {
    org.mockito.ArgumentCaptor<RunCommand> captor =
        org.mockito.ArgumentCaptor.forClass(RunCommand.class);
    verify(commandPublisher).publish(captor.capture());
    return captor.getValue();
  }

  // No Clock is injected into LoadTestService (deliberate — the watchdog reads the wall
  // clock via Instant.now()), and waiting out the real 15s threshold in a unit test is not
  // worth it. Reach into the liveness map and move this sub-run's last-seen timestamp into
  // the past so the next detectStuckSubRuns() treats it as silent. Test-only; changes no
  // production logic. A no-op once the entry has been cleaned up.
  @SuppressWarnings("unchecked")
  private void backdateLastActivity(String subId, Duration age) {
    try {
      Field field = LoadTestService.class.getDeclaredField("subIdLastActivity");
      field.setAccessible(true);
      Map<String, Instant> map = (Map<String, Instant>) field.get(service);
      if (map.containsKey(subId)) {
        map.put(subId, Instant.now().minus(age));
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not backdate watchdog clock", e);
    }
  }

  @SuppressWarnings("unchecked")
  private void putInStore(String id, TestResult result) {
    try {
      Field field = LoadTestService.class.getDeclaredField("store");
      field.setAccessible(true);
      ((Map<String, TestResult>) field.get(service)).put(id, result);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not seed store", e);
    }
  }

  private record Captured(List<Object> payloads, boolean completed) {}

  // SseEmitter's capture hook (ResponseBodyEmitter.initialize + its Handler interface) is
  // package-private in Spring MVC, so it cannot be named from this package. Drive it
  // reflectively through a dynamic proxy of that Handler to receive exactly what the
  // framework itself would on flush: the buffered send(s) and the completion signal. This
  // adds no production seam — it uses the real emitter contract, just accessed reflectively.
  private Captured drive(SseEmitter emitter) throws Exception {
    Class<?> handlerType =
        Class.forName(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler");

    List<Object> payloads = new ArrayList<>();
    boolean[] completed = {false};

    InvocationHandler recorder =
        (proxy, method, args) -> {
          switch (method.getName()) {
            case "send" -> payloads.addAll(extractData(args[0]));
            case "complete" -> completed[0] = true;
            default -> {
              // onTimeout / onError / completeWithError: irrelevant to these assertions.
            }
          }
          return null;
        };

    Object handler =
        Proxy.newProxyInstance(
            handlerType.getClassLoader(), new Class<?>[] {handlerType}, recorder);

    Method initialize = ResponseBodyEmitter.class.getDeclaredMethod("initialize", handlerType);
    initialize.setAccessible(true);
    initialize.invoke(emitter, handler);

    return new Captured(payloads, completed[0]);
  }

  // A buffered SSE send arrives as a Set<DataWithMediaType> (event name + payload). Pull
  // the payload objects back out so the test can assert on the replayed TestResult itself.
  private List<Object> extractData(Object sendArg) throws Exception {
    List<Object> data = new ArrayList<>();
    if (sendArg instanceof Iterable<?> items) {
      for (Object item : items) {
        Method getData = item.getClass().getMethod("getData");
        Object value = getData.invoke(item);
        // SseEmitter interleaves control tokens ("data:", the event name, "\n\n") with the
        // actual payload; keep only the domain object.
        if (value instanceof TestResult) {
          data.add(value);
        }
      }
    }
    return data;
  }
}
