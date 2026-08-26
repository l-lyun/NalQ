package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {
  List<QuizSet> findAllByStatus(QuizSetStatus status);

  Optional<QuizSet> findByPublicIdAndUserId(String publicId, long userId);

  Optional<QuizSet> findFirstByLearningMaterialIdAndUserIdAndStatusOrderByCreatedAtDesc(
      long learningMaterialId, long userId, QuizSetStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from QuizSet q where q.publicId = :publicId and q.userId = :userId")
  Optional<QuizSet> findOwnedForUpdate(
      @Param("publicId") String publicId, @Param("userId") long userId);
}
