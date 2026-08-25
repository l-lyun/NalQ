package com.openmd.server.learningmaterial.repository;

import com.openmd.server.learningmaterial.domain.LearningMaterial;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, Long> {

  Optional<LearningMaterial> findByUserIdAndIdempotencyKeyHash(
      long userId, byte[] idempotencyKeyHash);

  Optional<LearningMaterial> findByIdAndUserId(long id, long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select material from LearningMaterial material where material.id = :id and material.userId ="
          + " :userId")
  Optional<LearningMaterial> findOwnedForUpdate(@Param("id") long id, @Param("userId") long userId);
}
