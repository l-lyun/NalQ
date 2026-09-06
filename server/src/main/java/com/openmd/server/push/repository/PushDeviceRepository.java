package com.openmd.server.push.repository;

import com.openmd.server.push.domain.PushDevice;
import com.openmd.server.push.domain.PushProvider;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

  Optional<PushDevice> findByInstallationId(String installationId);

  Optional<PushDevice> findByProviderAndPushTokenDigest(
      PushProvider provider, String pushTokenDigest);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from PushDevice d where d.installationId = :installationId")
  Optional<PushDevice> findByInstallationIdForUpdate(
      @Param("installationId") String installationId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select d from PushDevice d where d.provider = :provider"
          + " and d.pushTokenDigest = :pushTokenDigest")
  Optional<PushDevice> findByProviderAndPushTokenDigestForUpdate(
      @Param("provider") PushProvider provider,
      @Param("pushTokenDigest") String pushTokenDigest);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from PushDevice d where d.installationId in :installationIds order by d.id")
  List<PushDevice> findAllByInstallationIdInForUpdate(
      @Param("installationIds") List<String> installationIds);

  List<PushDevice> findAllBySessionId(String sessionId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select d from PushDevice d where d.sessionId = :sessionId"
          + " and d.status = com.openmd.server.push.domain.PushDeviceStatus.ACTIVE order by d.id")
  List<PushDevice> findAllActiveBySessionIdForUpdate(@Param("sessionId") String sessionId);

  List<PushDevice> findAllByUserId(long userId);

  @Modifying(flushAutomatically = true)
  @Query("delete from PushDevice d where d.userId = :userId")
  int deleteAllByUserId(@Param("userId") long userId);
}
