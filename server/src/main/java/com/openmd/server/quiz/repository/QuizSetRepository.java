package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {
  Optional<QuizSet> findByPublicIdAndUserId(String publicId, long userId);

  Optional<QuizSet> findFirstByLearningMaterialIdAndUserIdAndStatusOrderByCreatedAtDesc(
      long learningMaterialId, long userId, QuizSetStatus status);

  boolean existsByLearningMaterialIdAndUserIdAndStatus(
      long learningMaterialId, long userId, QuizSetStatus status);

  @Query("select distinct q.learningMaterialId from QuizSet q where q.userId = :userId"
      + " and q.status = :status and q.learningMaterialId in :learningMaterialIds")
  List<Long> findLearningMaterialIdsByUserIdAndStatusAndLearningMaterialIdIn(
      @Param("userId") long userId,
      @Param("status") QuizSetStatus status,
      @Param("learningMaterialIds") Collection<Long> learningMaterialIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from QuizSet q where q.publicId = :publicId and q.userId = :userId")
  Optional<QuizSet> findOwnedForUpdate(
      @Param("publicId") String publicId, @Param("userId") long userId);
}
