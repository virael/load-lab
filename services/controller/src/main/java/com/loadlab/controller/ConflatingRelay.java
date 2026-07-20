package com.loadlab.controller;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

// Decouples a shared, latency-sensitive producer thread (the Kafka listener, which
// processes metrics for EVERY running test) from a slow downstream consumer. A
// dedicated virtual thread drains a single-slot, CONFLATING buffer: if the consumer
// cannot keep up, older pending values are silently replaced by newer ones. offer()
// never blocks, no matter how slow the sink is.
//
// Dropping intermediate values is the right trade for a live dashboard: it wants to
// know where the run is NOW, not to replay every step it missed. The terminal
// snapshot is never at risk — it is always the last value offered, and conflation
// only ever discards values that something newer superseded.
//
// Deliberate contrast with E0.4, where virtual threads were avoided so the
// "one thread per virtual user" ceiling stayed visible. Hundreds of mostly-idle
// connected clients are the textbook case FOR them.
class ConflatingRelay<T> {

  private final Consumer<T> sink;
  private final AtomicReference<T> pending = new AtomicReference<>();
  private final Thread worker;
  private volatile boolean closed = false;

  ConflatingRelay(Consumer<T> sink) {
    this.sink = sink;
    this.worker = Thread.ofVirtual().start(this::drainLoop);
  }

  // The only method the Kafka thread calls. A volatile write plus a cheap wake-up:
  // no lock, no queue, no wait — unbounded slowness downstream cannot reach back here.
  void offer(T value) {
    pending.set(value);
    LockSupport.unpark(worker);
  }

  private void drainLoop() {
    while (!closed) {
      T next = pending.getAndSet(null);
      if (next == null) {
        // A concurrent offer() between the getAndSet and here leaves a permit behind,
        // so this park() returns immediately rather than missing the wake-up.
        LockSupport.park();
        continue;
      }
      sink.accept(next);
    }
  }

  void close() {
    closed = true;
    worker.interrupt();
  }
}
