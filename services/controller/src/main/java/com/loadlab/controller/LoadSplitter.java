package com.loadlab.controller;

final class LoadSplitter {
  private LoadSplitter() {}

  // The remainder goes to the first workers: 10 VU over 3 workers is [4, 3, 3], not
  // [3, 3, 3], which would quietly drop a virtual user.
  static int[] computeShares(int virtualUsers, int workerCount) {
    int base = virtualUsers / workerCount;
    int remainder = virtualUsers % workerCount;
    int[] shares = new int[workerCount];
    for (int i = 0; i < workerCount; i++) {
      shares[i] = base + (i < remainder ? 1 : 0);
    }
    return shares;
  }
}
