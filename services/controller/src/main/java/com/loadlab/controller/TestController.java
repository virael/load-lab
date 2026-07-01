package com.loadlab.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tests")
public class TestController {

    private final LoadTestService service;

    public TestController(LoadTestService service) {
        this.service = service;
    }

    @PostMapping
    public TestResult startTest(@Valid @RequestBody TestRequest request) {
        return service.startTest(request);
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<TestResult> getResults(@PathVariable String id) {
        TestResult result = service.getResult(id);
        return result == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(result);
    }
}