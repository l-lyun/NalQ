package com.openmd.server.push.repository;

import com.openmd.server.push.domain.PushDelivery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeliveryRepository extends JpaRepository<PushDelivery, Long> {
  List<PushDelivery> findAllByNotificationId(long notificationId);

  @Modifying(flushAutomatically = true)
  @Query("delete from PushDelivery d where d.userId = :userId")
  int deleteAllByUserId(@Param("userId") long userId);
}
