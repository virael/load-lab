package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ConflatingRelayTest {

  @Test
  void offerNeverBlocksEvenWhileSinkIsBusy() throws InterruptedException {
    CountDownLatch sinkStarted = new CountDownLatch(1);
    CountDownLatch releaseSink = new CountDownLatch(1);

    var relay =
        new ConflatingRelay<Integer>(
            value -> {
              sinkStarted.countDown();
              try {
                releaseSink.await(); // stands in for a slow browser on a bad connection
              } catch (InterruptedException ignored) {
              }
            });

    relay.offer(1);
    assertThat(sinkStarted.await(2, TimeUnit.SECONDS)).isTrue();

    long start = System.nanoTime();
    relay.offer(2); // sink is still wedged on value 1 — these must NOT block
    relay.offer(3);
    relay.offer(4);
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertThat(elapsedMs).isLessThan(100);
    releaseSink.countDown();
    relay.close();
  }

  @Test
  void onlyTheLatestValueSurvivesWhenSinkCannotKeepUp() throws InterruptedException {
    List<Integer> received = new CopyOnWriteArrayList<>();
    CountDownLatch firstValueBeingProcessed = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicBoolean first = new AtomicBoolean(true);

    var relay =
        new ConflatingRelay<Integer>(
            value -> {
              if (first.compareAndSet(true, false)) {
                firstValueBeingProcessed.countDown();
                try {
                  releaseFirst.await();
                } catch (InterruptedException ignored) {
                }
              }
              received.add(value);
            });

    relay.offer(1);
    assertThat(firstValueBeingProcessed.await(2, TimeUnit.SECONDS)).isTrue();

    // Sink is stuck on "1". These three arrive before it frees up, so only the LAST
    // of them should ever actually be delivered.
    relay.offer(2);
    relay.offer(3);
    relay.offer(4);

    releaseFirst.countDown();

    await()
        .atMost(Duration.ofSeconds(2))
        .untilAsserted(() -> assertThat(received).containsExactly(1, 4));

    relay.close();
  }
}
