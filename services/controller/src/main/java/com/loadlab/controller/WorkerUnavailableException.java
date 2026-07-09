package com.loadlab.controller;

public class WorkerUnavailableException extends RuntimeException {
  public WorkerUnavailableException(String message) {
    super(message);
  }

  public WorkerUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
