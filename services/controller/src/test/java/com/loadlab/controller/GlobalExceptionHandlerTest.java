package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// Pure unit test, no Spring context: the advice is a plain method mapping one exception
// to one response. The only thing worth proving is the contract the frontend depends on
// — a WorkerUnavailableException becomes a 503 with the exact ErrorResponse shape — and
// that needs nothing more than calling the method.
class GlobalExceptionHandlerTest {

  @Test
  void mapsWorkerUnavailableToServiceUnavailableWithErrorShape() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();

    ResponseEntity<GlobalExceptionHandler.ErrorResponse> response =
        handler.handleWorkerUnavailable(
            new WorkerUnavailableException("No healthy worker found before publishing command"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    // The code is a stable machine-readable contract; the message is passed through from
    // the exception so the caller sees why, not just that.
    assertThat(response.getBody().code()).isEqualTo("WORKER_UNAVAILABLE");
    assertThat(response.getBody().message())
        .isEqualTo("No healthy worker found before publishing command");
  }
}
