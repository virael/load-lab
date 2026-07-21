# Phase 7 — Thread-per-VU vs Reactive Benchmark

Both engines run in one JVM launch, one after the other, against the same target
(`LoadEngineBenchmark`) — same hardware, same JIT state, no commit-juggling.

**Hardware:** AMD Ryzen 7 5800U (16 logical cores), 30 GiB RAM, Ubuntu 22.04.5
(kernel 6.15.1), OpenJDK 21.0.11
**Target:** SUT `/simulate`, `latencyMs=50` (fixed), `errorRate=0.0`
**Virtual users:** 500 | Warm-up: 5s (discarded) | Measured: 15s

> The SUT was started with a raised connector ceiling
> (`--server.tomcat.max-connections=5000 --server.tomcat.threads.max=1000`)
> **on purpose**. Its default (`max-connections=50`) is a hard wall at 1/10 of the
> VU count, which makes the *target* the bottleneck and the *engine* invisible:
> under the default cap, thread-per-VU degrades to p99 ≈ 25 s and the reactive phase
> records 0 completed requests (every request is still in flight when the 15 s window
> closes and gets cancelled). Raising the ceiling makes this a comparison of the two
> **client engines**, which is the point of E7.2.

| Metric                  | Thread-per-VU | Reactive (WebClient) |
|-------------------------|---------------|----------------------|
| Total requests          | 146,813       | 146,386              |
| Requests/sec            | 9,755.8       | 9,758.5              |
| p50 (ms)                | 50            | 50                   |
| p95 (ms)                | 53            | 52                   |
| p99 (ms)                | 58            | 60                   |
| Peak live JVM threads   | 543           | 34                   |

## Observations

- **Throughput and latency are effectively identical** at 500 VU — within noise on
  every column. Both engines are target-bound: the SUT answers in ~50 ms and neither
  client is the limiter, so both converge on the same ~9,750 req/s. This is expected,
  not a disappointment: at moderate concurrency the concurrency model does not change
  how fast a 50 ms target can answer.

- **The thread count is the whole story: 543 vs 34.** Thread-per-VU pays one real OS
  thread per virtual user (500 pool threads + framework/GC), exactly as designed back
  in E0.4. The reactive engine does the same work on a handful of event-loop threads
  (~2× the 16 cores) plus a few for Reactor/HdrHistogram. Same output, ~16× fewer
  threads — that is the resource ceiling E7.1 moved from "OS thread count" to
  "in-flight requests".

- **Where the throughput gap would appear:** not here. The thread-per-VU model starts
  to choke on context-switching between hundreds/thousands of threads only at much
  higher VU counts, where the reactive model keeps running on the same small pool.
  Reproducing that on this SUT is blocked by the target itself — even with the raised
  ceiling, a 50 ms/request target caps aggregate throughput long before the client's
  thread-scheduling cost dominates. Demonstrating the divergence cleanly needs either
  a near-zero-latency target or VU counts (several thousand) where the 500-thread
  model's scheduling overhead becomes visible against an otherwise unsaturated target.
