package com.openmd.server.push.config;

import com.openmd.server.push.service.PushDeliveryTransaction;
import com.openmd.server.push.service.PushDeliveryWorker;
import com.openmd.server.push.service.PushRetentionService;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public final class PushScheduler {

  private static final Logger log = LoggerFactory.getLogger(PushScheduler.class);
  private final PushDeliveryWorker worker;
  private final PushDeliveryTransaction transactions;
  private final PushRetentionService retention;
  private final boolean sendEnabled;
  private final int batchSize;
  private final Clock clock;

  public PushScheduler(
      PushDeliveryWorker worker,
      PushDeliveryTransaction transactions,
      PushRetentionService retention,
      boolean sendEnabled,
      int batchSize,
      Clock clock) {
    this.worker = worker;
    this.transactions = transactions;
    this.retention = retention;
    this.sendEnabled = sendEnabled;
    this.batchSize = batchSize;
    this.clock = clock;
  }

  @Scheduled(
      fixedDelayString = "${openmd.push.tick-delay:5s}",
      scheduler = "pushTaskScheduler")
  public void deliver() {
    safely(
        "lease-recovery", () -> transactions.recoverExpiredLeases(clock.instant(), batchSize));
    if (sendEnabled) {
      safely("send", worker::sendDue);
    }
    safely("receipt", worker::checkReceipts);
  }

  @Scheduled(
      fixedDelayString = "${openmd.push.retention-delay:1h}",
      scheduler = "pushTaskScheduler")
  public void retain() {
    safely("retention", retention::deleteExpired);
  }

  private void safely(String operation, Runnable task) {
    try {
      task.run();
    } catch (RuntimeException exception) {
      log.warn("Push scheduler operation failed: {}", operation);
    }
  }
}
