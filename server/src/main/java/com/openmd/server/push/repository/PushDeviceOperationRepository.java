package com.openmd.server.push.repository;

import com.openmd.server.push.domain.PushDeviceOperation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeviceOperationRepository extends JpaRepository<PushDeviceOperation, Long> {
  Optional<PushDeviceOperation> findByInstallationIdAndOperationId(
      String installationId, String operationId);

  @Modifying(flushAutomatically = true)
  @Query("delete from PushDeviceOperation operation where operation.subjectUserId = :userId")
  int deleteAllBySubjectUserId(@Param("userId") long userId);
}
