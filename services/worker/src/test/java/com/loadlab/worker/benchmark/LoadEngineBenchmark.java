package com.loadlab.worker.benchmark;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.HdrHistogram.Histogram;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Standalone comparison harness — NOT a JUnit test, it does not run on `mvn test`.
 * Run manually:
 *   ./mvnw test-compile exec:java \
 *     -Dexec.mainClass=com.loadlab.worker.benchmark.LoadEngineBenchmark \
 *     -Dexec.classpathScope=test
 *
 * Recreates a minimal thread-per-VU engine here, OUTSIDE production code — E7.1
 * removed it from RunExecutorService. Both engines run in the SAME JVM launch, one
 * after the other, against the same target: same hardware, same JIT state, no
 * commit-juggling.
 */
public class LoadEngineBenchmark {

  private static final String TARGET_URL = "http://localhost:8081/simulate";
  private static final int VIRTUAL_USERS = 500;
  private static final int WARMUP_SECONDS = 5;
  private static final int MEASURE_SECONDS = 15;

  public static void main(String[] args) throws Exception {
    System.out.println(
        "Target: "
            + TARGET_URL
            + " | VUs: "
            + VIRTUAL_USERS
            + " | warm-up: "
            + WARMUP_SECONDS
            + "s | measured: "
            + MEASURE_SECONDS
            + "s");
    System.out.println();

    report("Thread-per-VU (java.net.http.HttpClient)", LoadEngineBenchmark::threadPerVuEngine);
    report("Reactive (WebClient / Reactor Netty)", LoadEngineBenchmark::reactiveEngine);
  }

  private interface Engine {
    Histogram run(int virtualUsers, int seconds) throws Exception;
  }

  private static void report(String label, Engine engine) throws Exception {
    System.out.println("=== " + label + " ===");
    System.out.println("Warm-up (discarded)...");
    engine.run(VIRTUAL_USERS, WARMUP_SECONDS);
    Thread.sleep(2000); // let the system settle between phases

    System.out.println("Measuring...");
    AtomicInteger peakThreads = new AtomicInteger();
    AtomicBoolean samplerDone = new AtomicBoolean(false);
    Thread sampler =
        new Thread(
            () -> {
              while (!samplerDone.get()) {
                peakThreads.updateAndGet(prev -> Math.max(prev, Thread.activeCount()));
                try {
                  Thread.sleep(100);
                } catch (InterruptedException e) {
                  break;
                }
              }
            });
    sampler.setDaemon(true);
    sampler.start();

    long startNanos = System.nanoTime();
    Histogram histogram = engine.run(VIRTUAL_USERS, MEASURE_SECONDS);
    double actualSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
    samplerDone.set(true);

    System.out.printf("Total requests: %d%n", histogram.getTotalCount());
    System.out.printf("Requests/sec:   %.1f%n", histogram.getTotalCount() / actualSeconds);
    System.out.printf(
        "p50/p95/p99 ms: %d / %d / %d%n",
        histogram.getValueAtPercentile(50),
        histogram.getValueAtPercentile(95),
        histogram.getValueAtPercentile(99));
    System.out.printf("Peak live JVM threads during measurement: %d%n", peakThreads.get());
    System.out.println();
  }

  private static Histogram newHistogram() {
    // Auto-resizing: a single latency outlier past a fixed ceiling would otherwise
    // throw ArrayIndexOutOfBoundsException and abort the whole benchmark.
    Histogram histogram = new Histogram(2);
    histogram.setAutoResize(true);
    return histogram;
  }

  // --- Old model: one OS thread per virtual user ---
  private static Histogram threadPerVuEngine(int virtualUsers, int seconds)
      throws InterruptedException {
    Histogram histogram = newHistogram();
    var http = java.net.http.HttpClient.newHttpClient();
    ExecutorService pool = Executors.newFixedThreadPool(virtualUsers);
    long endTime = System.nanoTime() + seconds * 1_000_000_000L;

    for (int i = 0; i < virtualUsers; i++) {
      pool.submit(
          () -> {
            HttpRequest request = HttpRequest.newBuilder(URI.create(TARGET_URL)).GET().build();
            while (System.nanoTime() < endTime) {
              long start = System.nanoTime();
              try {
                http.send(request, HttpResponse.BodyHandlers.discarding());
              } catch (Exception ignored) {
                // counted as a completed attempt regardless, same as the old engine
              }
              synchronized (histogram) {
                histogram.recordValue(Math.max(1, (System.nanoTime() - start) / 1_000_000));
              }
            }
          });
    }
    pool.shutdown();
    pool.awaitTermination(seconds + 10L, TimeUnit.SECONDS);
    // shutdownNow (non-blocking): reaps this engine's selector threads so they do not
    // linger into the reactive phase and inflate its peak-thread reading. close()
    // would block until every in-flight request drains, which stalls hard against a
    // connection-capped target.
    http.shutdownNow();
    return histogram;
  }

  // --- New model: WebClient, concurrency bounded by flatMap ---
  private static Histogram reactiveEngine(int virtualUsers, int seconds) {
    Histogram histogram = newHistogram();
    // Size the pool to virtualUsers so the CLIENT is not the limiter. Bare
    // HttpClient.create() would cap at ~2x cores (~32) connections, silently
    // throttling the reactive engine far below the VU count and defeating the point
    // of the comparison. Mirrors production WebClientConfig.
    ConnectionProvider provider =
        ConnectionProvider.builder("benchmark-pool")
            .maxConnections(virtualUsers)
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .build();
    WebClient webClient =
        WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create(provider)))
            .build();

    Flux.range(0, Integer.MAX_VALUE)
        .flatMap(
            i -> {
              long start = System.nanoTime();
              return webClient
                  .get()
                  .uri(TARGET_URL)
                  .exchangeToMono(resp -> resp.releaseBody())
                  .doOnTerminate(
                      () -> {
                        synchronized (histogram) {
                          histogram.recordValue(
                              Math.max(1, (System.nanoTime() - start) / 1_000_000));
                        }
                      })
                  .onErrorResume(e -> Mono.empty());
            },
            virtualUsers)
        .takeUntilOther(Mono.delay(Duration.ofSeconds(seconds)))
        .then()
        .block();

    provider.dispose();
    return histogram;
  }
}
