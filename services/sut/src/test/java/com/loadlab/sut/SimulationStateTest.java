package com.loadlab.sut;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SimulationStateTest {

  @Test
  void startsWithValuesFromProperties() {
    var defaults = new SimulationProperties(10, 100, 0.05);
    var state = new SimulationState(defaults);

    assertThat(state.minLatencyMs()).isEqualTo(10);
    assertThat(state.maxLatencyMs()).isEqualTo(100);
    assertThat(state.errorRate()).isEqualTo(0.05);
  }

  @Test
  void updateChangesOnlyProvidedFields() {
    var state = new SimulationState(new SimulationProperties(10, 100, 0.0));

    state.update(null, null, 0.5);

    assertThat(state.minLatencyMs()).isEqualTo(10);
    assertThat(state.maxLatencyMs()).isEqualTo(100);
    assertThat(state.errorRate()).isEqualTo(0.5);
  }

  @Test
  void updateWithAllNullsChangesNothing() {
    var state = new SimulationState(new SimulationProperties(10, 100, 0.2));

    state.update(null, null, null);

    assertThat(state.minLatencyMs()).isEqualTo(10);
    assertThat(state.maxLatencyMs()).isEqualTo(100);
    assertThat(state.errorRate()).isEqualTo(0.2);
  }

  @Test
  void survivesConcurrentUpdatesWithoutCorruption() throws InterruptedException {
    var state = new SimulationState(new SimulationProperties(0, 0, 0.0));
    int threads = 50;
    var pool = Executors.newFixedThreadPool(threads);
    var ready = new CountDownLatch(threads);
    var start = new CountDownLatch(1);
    var done = new CountDownLatch(threads);

    for (int i = 0; i < threads; i++) {
      long value = i;
      pool.submit(
          () -> {
            ready.countDown();
            try {
              start.await();
              state.update(value, value, null);
            } catch (InterruptedException ignored) {
            } finally {
              done.countDown();
            }
          });
    }

    ready.await();
    start.countDown();
    done.await(5, TimeUnit.SECONDS);
    pool.shutdown();

    assertThat(state.minLatencyMs()).isEqualTo(state.maxLatencyMs());
  }
}
