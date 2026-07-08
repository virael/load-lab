package com.loadlab.worker;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/runs")
public class RunController {

    private final RunExecutorService service;

    public RunController(RunExecutorService service) {
        this.service = service;
    }

    @PostMapping
    public RunResult startRun(@RequestBody RunRequest request) {
        return service.startRun(request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RunResult> getResult(@PathVariable String id) {
        RunResult result = service.getResult(id);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        return service.subscribe(id);
    }
}