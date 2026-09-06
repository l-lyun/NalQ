package com.openmd.server.push.repository;

import com.openmd.server.push.domain.PushDelivery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {
  List<PushDelivery> findAllByNotificationId(long notificationId);
}
