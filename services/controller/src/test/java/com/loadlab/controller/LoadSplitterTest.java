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
}
