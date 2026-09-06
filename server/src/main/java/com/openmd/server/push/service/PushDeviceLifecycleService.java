package com.openmd.server.push.service;

import com.openmd.server.push.domain.PushDevice;
import com.openmd.server.push.repository.PushDeviceOperationRepository;
import com.openmd.server.push.repository.PushDeviceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushDeviceLifecycleService implements PushDeviceLifecycle {

  private final ObjectProvider<PushDeviceRepository> devices;
  private final ObjectProvider<PushDeviceOperationRepository> operations;
  private final Clock clock;

  public PushDeviceLifecycleService(
      ObjectProvider<PushDeviceRepository> devices,
      ObjectProvider<PushDeviceOperationRepository> operations,
      ObjectProvider<Clock> clock) {
    this.devices = devices;
    this.operations = operations;
    this.clock = clock.getIfAvailable(Clock::systemUTC);
  }

  @Override
  @Transactional
  public void revokeSession(String sessionId) {
    PushDeviceRepository deviceRepository = devices.getIfAvailable();
    if (deviceRepository == null) {
      return;
    }
    List<PushDevice> activeDevices =
        deviceRepository.findAllActiveBySessionIdForUpdate(sessionId);
    Instant now = clock.instant();
    activeDevices.forEach(device -> device.revoke(now));
    deviceRepository.saveAll(activeDevices);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void deleteForUser(long userId) {
    PushDeviceOperationRepository operationRepository = operations.getIfAvailable();
    PushDeviceRepository deviceRepository = devices.getIfAvailable();
    if (operationRepository == null || deviceRepository == null) {
      return;
    }
    operationRepository.deleteAllBySubjectUserId(userId);
    deviceRepository.deleteAllByUserId(userId);
  }
}
