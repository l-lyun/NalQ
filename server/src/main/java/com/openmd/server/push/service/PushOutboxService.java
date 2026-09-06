package com.openmd.server.push.service;

import com.openmd.server.notification.domain.QuizGenerationNotification;
import com.openmd.server.push.domain.PushDelivery;
import com.openmd.server.push.domain.PushDeviceStatus;
import com.openmd.server.push.repository.PushDeliveryRepository;
import com.openmd.server.push.repository.PushDeviceRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushOutboxService {
  private final ObjectProvider<PushDeviceRepository> devices;
  private final ObjectProvider<PushDeliveryRepository> deliveries;
  private final boolean deliveryEnabled;

  public PushOutboxService(ObjectProvider<PushDeviceRepository> devices,
      ObjectProvider<PushDeliveryRepository> deliveries,
      @Value("${openmd.push.delivery-enabled:false}") boolean deliveryEnabled) {
    this.devices = devices;
    this.deliveries = deliveries;
    this.deliveryEnabled = deliveryEnabled;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(QuizGenerationNotification notification) {
    if (!deliveryEnabled) return;
    for (var device : devices.getObject().findAllByUserId(notification.getUserId())) {
      if (device.getStatus() == PushDeviceStatus.ACTIVE) {
        deliveries.getObject().save(PushDelivery.pending(notification.getId(), notification.getUserId(),
            device, notification.getCreatedAt()));
      }
    }
  }
}
