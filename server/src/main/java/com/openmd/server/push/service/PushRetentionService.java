package com.openmd.server.push.service;

import com.openmd.server.push.repository.PushRetentionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

public class PushRetentionService {

  private static final Duration DELIVERY_RETENTION = Duration.ofDays(30);
  private static final Duration INACTIVE_DEVICE_RETENTION = Duration.ofDays(30);

  private final PushRetentionStore store;
  private final Clock clock;
  private final int batchSize;

  public PushRetentionService(PushRetentionStore store, Clock clock, int batchSize) {
    this.store = store;
    this.clock = clock;
    this.batchSize = batchSize;
  }

  @Transactional
  public void deleteExpired() {
    Instant now = clock.instant();
    store.deleteDeliveriesCreatedBefore(now.minus(DELIVERY_RETENTION), batchSize);
    store.deleteOperationsExpiredAtOrBefore(now, batchSize);
    store.deleteInactiveDevicesBefore(now.minus(INACTIVE_DEVICE_RETENTION), batchSize);
  }
}
