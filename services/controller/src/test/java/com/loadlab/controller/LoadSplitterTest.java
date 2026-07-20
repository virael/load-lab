package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoadSplitterTest {

  @Test
  void distributesRemainderToFirstWorkers() {
    assertThat(LoadSplitter.computeShares(10, 3)).containsExactly(4, 3, 3);
  }

  @Test
  void splitsEvenlyWhenDivisible() {
    assertThat(LoadSplitter.computeShares(9, 3)).containsExactly(3, 3, 3);
  }

  @Test
  void neverLosesOrDuplicatesVirtualUsers() {
    int[] shares = LoadSplitter.computeShares(17, 5);
    int sum = 0;
    for (int share : shares) sum += share;
    assertThat(sum).isEqualTo(17);
  }

  @Test
  void handlesFewerVirtualUsersThanWorkers() {
    assertThat(LoadSplitter.computeShares(2, 5)).containsExactly(1, 1, 0, 0, 0);
  }

  @Test
  void computesWorkerCountBasedOnCapacity() {
    assertThat(LoadSplitter.computeWorkerCount(50_000, 5_000, 20)).isEqualTo(10);
  }

  @Test
  void roundsUpSoNoVirtualUserIsLeftUnassigned() {
    // 50_001 needs an eleventh worker for the single leftover user, not ten.
    assertThat(LoadSplitter.computeWorkerCount(50_001, 5_000, 20)).isEqualTo(11);
  }

  @Test
  void neverExceedsMaxWorkers() {
    assertThat(LoadSplitter.computeWorkerCount(1_000_000, 5_000, 20)).isEqualTo(20);
  }

  @Test
  void alwaysAtLeastOneWorker() {
    assertThat(LoadSplitter.computeWorkerCount(1, 5_000, 20)).isEqualTo(1);
  }
}
