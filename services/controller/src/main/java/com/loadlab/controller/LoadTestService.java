package com.loadlab.controller;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;

@Service
public class LoadTestService {

    private final Map<String, TestResult> store = new ConcurrentHashMap<>();

    private final ExecutorService orchestrator = Executors.newCachedThreadPool();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public TestResult startTest(TestRequest req) {
        String id = UUID.randomUUID().toString();
        store.put(id, new TestResult(id, "PENDING", 0, 0.0, 0));
        orchestrator.submit(() -> runTest(id, req));
        return store.get(id);
    }

    public TestResult getResult(String id) {
        return store.get(id);
    }

    private void runTest(String id, TestRequest req) {
        var totalRequests = new LongAdder();
        var errors = new LongAdder();
        var totalLatencyNanos = new LongAdder();

        store.put(id, new TestResult(id, "RUNNING", 0, 0.0, 0));

        ExecutorService pool = Executors.newFixedThreadPool(req.virtualUsers());
        long endTime = System.nanoTime() + req.durationSeconds() * 1_000_000_000L;

        HttpRequest request = HttpRequest.newBuilder(URI.create(req.targetUrl()))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        List<Future<?>> vus = new ArrayList<>();
        for (int i = 0; i < req.virtualUsers(); i++) {
            vus.add(pool.submit(() -> {
                while (System.nanoTime() < endTime) {
                    long start = System.nanoTime();
                    try {
                        HttpResponse<Void> resp = http.send(request, HttpResponse.BodyHandlers.discarding());
                        if (resp.statusCode() >= 400)
                            errors.increment();
                    } catch (Exception e) {
                        errors.increment();
                    } finally {
                        totalRequests.increment();
                        totalLatencyNanos.add(System.nanoTime() - start);
                    }
                }
            }));
        }

        for (Future<?> f : vus) {
            try {
                f.get();
            } catch (Exception ignored) {
            }
        }
        pool.shutdown();

        long total = totalRequests.sum();
        double avgMs = total == 0 ? 0.0
                : (totalLatencyNanos.sum() / (double) total) / 1_000_000.0;
        store.put(id, new TestResult(id, "DONE", total, avgMs, errors.sum()));
    }
}