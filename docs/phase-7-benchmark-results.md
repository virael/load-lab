# Phase 7 — Thread-per-VU vs Reactive Benchmark

Both engines run in one JVM launch, one after the other, against the same target
(`LoadEngineBenchmark`, parameterised via `-Dbenchmark.virtualUsers`) — same hardware,
same JIT state, no commit-juggling.

**Hardware:** AMD Ryzen 7 5800U (16 logical cores), 30 GiB RAM, Ubuntu 22.04.5
(kernel 6.15.1), OpenJDK 21.0.11
**Target:** SUT `/simulate`, default latency (50 ms fixed) and error rate (0.0)
**Warm-up:** 5 s (discarded) | **Measured:** 15 s

> The SUT was started with a raised connector ceiling
> (`--server.tomcat.max-connections=12000 --server.tomcat.threads.max=6000`), keeping
> its default latency and error rate. Its shipped default (`max-connections=50`) is a
> hard wall far below the VU count that makes the *target* the bottleneck and the
> *engine* invisible (under it, thread-per-VU degrades to p99 ≈ 25 s and the reactive
> phase records 0 completed requests). Raising the ceiling — and only the ceiling —
> keeps this a comparison of the two client engines, which is the point.
>
> Caveat that shapes the 3000-VU numbers: the client and the (blocking, thread-per-
> request) SUT run on the **same 16 cores**. At 3000 VU the SUT alone needs thousands
> of live threads, so past a point both processes contend for the same CPU. The
> comparison stays fair (identical target for both engines) but the absolute
> throughput is co-location-bound, not a clean measure of client capacity.

## 500 virtual users

| Metric                  | Thread-per-VU | Reactive (WebClient) |
|-------------------------|---------------|----------------------|
| Total requests          | 147,806       | 147,946              |
| Requests/sec            | 9,820.1       | 9,841.1              |
| p50 (ms)                | 50            | 50                   |
| p95 (ms)                | 51            | 51                   |
| p99 (ms)                | 56            | 55                   |
| Peak live JVM threads   | 537           | 18                   |

## 3000 virtual users

| Metric                  | Thread-per-VU | Reactive (WebClient) |
|-------------------------|---------------|----------------------|
| Total requests          | 307,679       | 261,518              |
| Requests/sec            | 20,212.7      | 17,331.0             |
| p50 (ms)                | 133           | 172                  |
| p95 (ms)                | 226           | 263                  |
| p99 (ms)                | 305           | 387                  |
| Peak live JVM threads   | 3,143         | 18                   |

## Observations

At 500 virtual users both engines are indistinguishable on throughput and latency —
~9,800 req/s, p99 ≈ 55 ms, within noise on every column. The only difference is the
thread count: **537 vs 18**. The reactive engine's advantage does not show up in
throughput here, exactly as expected: 500 OS threads is well within what this machine
schedules without meaningful overhead, and both engines are simply target-bound at the
50 ms response time.

At 3000 virtual users the result is not the tidy "reactive pulls ahead" story, and it
is worth reporting honestly. Thread-per-VU actually came out **slightly faster** —
20,213 vs 17,331 req/s and lower latency (p99 305 vs 387 ms). What collapsed instead
was the resource cost: the reactive engine held a **flat 18 threads** (unchanged from
500 VU), while thread-per-VU scaled to **3,143 live OS threads**, one per virtual user
plus framework overhead. So at 3000 VU the reactive model sustained ~86% of the
throughput on ~0.6% of the threads. It did **not** win on raw speed on this setup —
plausibly because its ~16 event-loop threads (2× cores) become the ceiling once the
co-located blocking SUT is also consuming those same cores, whereas the thread-per-VU
client rides the OS scheduler across thousands of threads and squeezes out marginally
more work.

**Takeaway:** across 500→3000 VU on this hardware, the reactive engine's benefit is a
*constant, tiny thread footprint* (18 threads at any concurrency), not higher
throughput — a throughput crossover in reactive's favour did not appear by 3000 VU on
a single 16-core box shared with a blocking target. The thread-per-VU model's cost is
structural and plainly visible (3,143 live threads), which is the number that would
break first on a memory- or thread-limited host well before throughput does.
