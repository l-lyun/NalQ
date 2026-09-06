package com.openmd.server.push.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.openmd.server.push.service.PushDeliveryTransaction;
import com.openmd.server.push.service.PushDeliveryWorker;
import com.openmd.server.push.service.PushRetentionService;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class PushSchedulerTest {

  private static final Instant NOW = Instant.parse("2026-09-06T06:00:00Z");

  @Test
  void deliveryOffStillRecoversLeasesAndChecksReceiptsWithoutSendingNewPushes() {
    PushDeliveryWorker worker = org.mockito.Mockito.mock(PushDeliveryWorker.class);
    PushDeliveryTransaction transactions =
        org.mockito.Mockito.mock(PushDeliveryTransaction.class);
    PushRetentionService retention = org.mockito.Mockito.mock(PushRetentionService.class);
    PushScheduler scheduler =
        new PushScheduler(
            worker,
            transactions,
            retention,
            false,
            50,
            Clock.fixed(NOW, ZoneOffset.UTC));

    scheduler.deliver();

    verify(transactions).recoverExpiredLeases(NOW, 50);
    verify(worker, never()).sendDue();
    verify(worker).checkReceipts();
  }

  @Test
  void pushJobsExplicitlyUseTheDedicatedScheduler() throws Exception {
    for (String methodName : new String[] {"deliver", "retain"}) {
      Method method = PushScheduler.class.getMethod(methodName);
      assertEquals("pushTaskScheduler", method.getAnnotation(Scheduled.class).scheduler());
    }
    PushSchedulingConfiguration configuration = new PushSchedulingConfiguration();
    assertNotSame(configuration.pushTaskScheduler(), configuration.defaultTaskScheduler());
  }
}
