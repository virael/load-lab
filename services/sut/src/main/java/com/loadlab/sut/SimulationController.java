package com.loadlab.sut;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SimulationController {

    private final SimulationProperties properties;

    public SimulationController(SimulationProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/simulate")
    public ResponseEntity<String> simulate() throws InterruptedException {
        Thread.sleep(properties.latencyMs());

        boolean shouldFail = ThreadLocalRandom.current().nextDouble() < properties.errorRate();
        if (shouldFail) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("simulated failure");
        }
        return ResponseEntity.ok("ok");
    }
}