package com.loadlab.worker;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CommandListener {

  private final RunExecutorService runExecutorService;

  public CommandListener(RunExecutorService runExecutorService) {
    this.runExecutorService = runExecutorService;
  }

  @KafkaListener(topics = "test-commands", groupId = "worker-group")
  public void onCommand(RunCommand command) {
    RunRequest req =
        new RunRequest(command.targetUrl(), command.virtualUsers(), command.durationSeconds());
    runExecutorService.startRun(command.testId(), req);
  }
}
