package com.loadlab.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/tests")
public class TestController {

  private final LoadTestService service;
  private final TestRepository testRepository;

  public TestController(LoadTestService service, TestRepository testRepository) {
    this.service = service;
    this.testRepository = testRepository;
  }

  @PostMapping
  public TestResult startTest(@Valid @RequestBody TestRequest request) {
    return service.startTest(request);
  }

  @GetMapping
  public List<TestSummary> listRecent(@RequestParam(defaultValue = "50") int limit) {
    return testRepository.findRecent(limit);
  }

  @GetMapping("/{id}/results")
  public ResponseEntity<TestResult> getResults(@PathVariable String id) {
    TestResult result = service.getResult(id);
    return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
  }

  @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter stream(@PathVariable String id) {
    return service.subscribe(id);
  }
}
