package com.loadlab.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(WorkerUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleWorkerUnavailable(WorkerUnavailableException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ErrorResponse("WORKER_UNAVAILABLE", e.getMessage()));
  }

  public record ErrorResponse(String code, String message) {}
}
