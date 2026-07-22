package com.loadlab.worker;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class CommandListener {

  private final RunExecutorService runExecutorService;

  public CommandListener(RunExecutorService runExecutorService) {
    this.runExecutorService = runExecutorService;
  }

  // ack-mode=manual: the Acknowledgment is passed through to startRun, which commits
  // the offset only after the run finishes — not here, on receipt. Returning quickly
  // keeps the listener free to poll; the deferred ack keeps consumer lag honest.
  @KafkaListener(topics = "test-commands", groupId = "worker-group")
  public void onCommand(RunCommand command, Acknowledgment ack) {
    RunRequest req =
        new RunRequest(command.targetUrl(), command.virtualUsers(), command.durationSeconds());
    runExecutorService.startRun(command.subId(), req, command.rampDelayMs(), ack);
  }
}
