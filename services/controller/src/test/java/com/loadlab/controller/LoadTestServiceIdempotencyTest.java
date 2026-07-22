package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

// Pure unit test of onMetrics()'s idempotency guard: no Spring, no Kafka broker.
// @KafkaListener only matters when Spring wires the bean; the method itself is an
// ordinary public method callable directly. Collaborators are hand-written minimal
// subclasses (the project's fake style) rather than a mocking framework — each passes
// null to the real constructor, which is safe because the overridden methods never
// touch the null dependency.
class LoadTestServiceIdempotencyTest {

  static class RecordingResultPublisher extends ResultPublisher {
    final List<TestResult> published = new ArrayList<>();

    RecordingResultPublisher() {
      super(null);
    }

    @Override
    public void publish(TestResult result) {
      published.add(result);
    }
  }

  static class RecordingCommandPublisher extends CommandPublisher {
    final List<RunCommand> published = new ArrayList<>();

    RecordingCommandPublisher() {
      super(null);
    }

    @Override
    public void publish(RunCommand command) {
      published.add(command);
    }
  }

  static class AlwaysHealthyWorkerClient extends WorkerClient {
    AlwaysHealthyWorkerClient() {
      super("http://unused.invalid");
    }

    @Override
    public boolean isHealthy() {
      return true;
    }
  }

  static class NoOpTestRepository extends TestRepository {
    NoOpTestRepository() {
      super(null);
    }

    @Override
    public void insertPending(TestResult initial, TestRequest req) {}

    @Override
    public void updateProgress(TestResult result) {}
  }

  private LoadTestService newService(RecordingResultPublisher resultPublisher) {
    return new LoadTestService(
        new RecordingCommandPublisher(),
        new AlwaysHealthyWorkerClient(),
        resultPublisher,
        new NoOpTestRepository(),
        10, // workerCapacityVus
        20); // maxWorkersPerTest
  }

  @Test
  void secondDoneForSameSubIdIsIgnored() {
    var resultPublisher = new RecordingResultPublisher();
    var service = newService(resultPublisher);

    String testId = service.startTest(new TestRequest("http://target.invalid", 10, 5)).id();
    String subId = testId + "-0"; // computeWorkerCount(10, 10, 20) == 1 -> a single sub-run

    var first = new TestResult(subId, "DONE", 500, 20.0, 0, 15, 18, 20, null);
    service.onMetrics(first);

    TestResult afterFirst = service.getResult(testId);
    assertThat(afterFirst.status()).isEqualTo("DONE");
    assertThat(afterFirst.totalRequests()).isEqualTo(500);

    // Simulates a duplicate: a second worker finishes the same work with DIFFERENT numbers.
    var duplicate = new TestResult(subId, "DONE", 9999, 999.0, 50, 900, 950, 999, null);
    service.onMetrics(duplicate);

    TestResult afterDuplicate = service.getResult(testId);
    assertThat(afterDuplicate.totalRequests())
        .withFailMessage(
            "A duplicate overwrote an already-finalized result — the idempotency guard failed")
        .isEqualTo(500);
    assertThat(afterDuplicate.errors()).isZero();
  }

  @Test
  void runningUpdatesBeforeTerminalStateStillApplyNormally() {
    var resultPublisher = new RecordingResultPublisher();
    var service = newService(resultPublisher);

    String testId = service.startTest(new TestRequest("http://target.invalid", 10, 5)).id();
    String subId = testId + "-0";

    service.onMetrics(new TestResult(subId, "RUNNING", 100, 10.0, 0, 5, 8, 9, null));
    service.onMetrics(new TestResult(subId, "RUNNING", 250, 11.0, 0, 5, 8, 9, null));

    assertThat(service.getResult(testId).totalRequests()).isEqualTo(250);

    service.onMetrics(new TestResult(subId, "DONE", 500, 12.0, 0, 5, 8, 9, null));
    assertThat(service.getResult(testId).status()).isEqualTo("DONE");
    assertThat(service.getResult(testId).totalRequests()).isEqualTo(500);
  }

  @Test
  void publishesToResultTopicOnceNotTwiceForDuplicateCompletion() {
    var resultPublisher = new RecordingResultPublisher();
    var service = newService(resultPublisher);

    String testId = service.startTest(new TestRequest("http://target.invalid", 10, 5)).id();
    String subId = testId + "-0";

    service.onMetrics(new TestResult(subId, "DONE", 500, 20.0, 0, 15, 18, 20, null));
    service.onMetrics(new TestResult(subId, "DONE", 9999, 999.0, 50, 900, 950, 999, null));

    long doneCount =
        resultPublisher.published.stream()
            .filter(r -> testId.equals(r.id()) && "DONE".equals(r.status()))
            .count();

    assertThat(doneCount)
        .withFailMessage(
            "Expected exactly one DONE publish to test-results; the duplicate must not produce a second")
        .isEqualTo(1);
  }
}
