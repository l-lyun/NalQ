package com.openmd.server.push.service;

import com.openmd.server.push.repository.PushRetentionStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.IntSupplier;

public class PushRetentionService {

  private static final Duration DELIVERY_RETENTION = Duration.ofDays(30);
  private static final Duration INACTIVE_DEVICE_RETENTION = Duration.ofDays(30);
  private static final int MAX_BATCHES_PER_TYPE_PER_RUN = 100;

  private final PushRetentionStore store;
  private final Clock clock;
  private final int batchSize;

  public PushRetentionService(PushRetentionStore store, Clock clock, int batchSize) {
    this.store = store;
    this.clock = clock;
    this.batchSize = batchSize;
  }

  public void deleteExpired() {
    Instant now = clock.instant();
    drain(() -> store.deleteDeliveriesCreatedBefore(now.minus(DELIVERY_RETENTION), batchSize));
    drain(() -> store.deleteOperationsExpiredAtOrBefore(now, batchSize));
    drain(() -> store.deleteInactiveDevicesBefore(now.minus(INACTIVE_DEVICE_RETENTION), batchSize));
  }

  private void drain(IntSupplier deleteBatch) {
    for (int batch = 0; batch < MAX_BATCHES_PER_TYPE_PER_RUN; batch++) {
      if (deleteBatch.getAsInt() < batchSize) {
        return;
      }
    }
  }
}
