package com.openmd.server.push.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.openmd.server.notification.domain.QuizGenerationNotification;
import com.openmd.server.push.domain.PushDevice;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.repository.PushDeliveryRepository;
import com.openmd.server.push.repository.PushDeviceRepository;
import com.openmd.server.quiz.domain.entity.QuizSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class PushOutboxServiceTest {
  private final PushDeviceRepository devices = mock(PushDeviceRepository.class);
  private final PushDeliveryRepository deliveries = mock(PushDeliveryRepository.class);

  @Test
  void disabledDeliveryDoesNotQueryDevicesOrCreateHistoricalJobs() {
    service(false).enqueue(notification());
    verifyNoInteractions(devices, deliveries);
  }

  @Test
  void fansOutToEachActiveDeviceWithOriginalNotificationExpiration() {
    var one = device(1);
    var two = device(2);
    var inactive = device(3);
    inactive.revoke(Instant.now());
    when(devices.findAllByUserId(7L)).thenReturn(List.of(one, two, inactive));
    var notification = notification();
    service(true).enqueue(notification);
    var captor = org.mockito.ArgumentCaptor.forClass(com.openmd.server.push.domain.PushDelivery.class);
    verify(deliveries, times(2)).save(captor.capture());
    assertEquals(List.of(1L, 2L), captor.getAllValues().stream().map(d -> d.getDeviceId()).toList());
    assertTrue(captor.getAllValues().stream()
        .allMatch(d -> d.getExpiresAt().equals(notification.getCreatedAt().plusSeconds(3600))));
  }

  private PushOutboxService service(boolean enabled) {
    var beans = new StaticListableBeanFactory();
    beans.addBean("devices", devices);
    beans.addBean("deliveries", deliveries);
    return new PushOutboxService(beans.getBeanProvider(PushDeviceRepository.class),
        beans.getBeanProvider(PushDeliveryRepository.class), enabled);
  }

  private QuizGenerationNotification notification() {
    var n = QuizGenerationNotification.from(QuizSet.ready(7L, 1L, "퀴즈"));
    ReflectionTestUtils.setField(n, "id", 1L);
    ReflectionTestUtils.setField(n, "createdAt", Instant.parse("2026-09-06T00:00:00Z"));
    return n;
  }

  private PushDevice device(long id) {
    var d = PushDevice.registered("installation-" + id, "digest", 7L, "session", "binding-" + id,
        PushPlatform.IOS, PushProvider.EXPO, "token-" + id, "digest-" + id);
    ReflectionTestUtils.setField(d, "id", id);
    return d;
  }
}
