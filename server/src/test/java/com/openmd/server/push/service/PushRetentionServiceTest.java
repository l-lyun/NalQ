package com.openmd.server.push.service;

import static org.mockito.Mockito.inOrder;

import com.openmd.server.push.repository.PushRetentionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PushRetentionServiceTest {

  @Test
  void deletesDeliveriesOperationsAndInactiveDevicesWithIndependentRetentionCutoffs() {
    Instant now = Instant.parse("2026-09-06T06:00:00Z");
    PushRetentionStore store = org.mockito.Mockito.mock(PushRetentionStore.class);
    PushRetentionService service =
        new PushRetentionService(store, Clock.fixed(now, ZoneOffset.UTC), 500);

    service.deleteExpired();

    InOrder order = inOrder(store);
    order.verify(store).deleteDeliveriesCreatedBefore(now.minusSeconds(30L * 86400L), 500);
    order.verify(store).deleteOperationsExpiredAtOrBefore(now, 500);
    order.verify(store).deleteInactiveDevicesBefore(now.minusSeconds(30L * 86400L), 500);
  }
}
