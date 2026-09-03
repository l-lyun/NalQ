package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {
  List<QuizSet> findAllByUserId(long userId);

  Page<QuizSet> findAllByUserId(long userId, Pageable pageable);

  Page<QuizSet> findAllByUserIdAndStatusNot(
      long userId, QuizSetStatus status, Pageable pageable);

  List<QuizSet> findAllByPublicIdInAndUserId(Collection<String> publicIds, long userId);

  @Query(
      "select count(q) from QuizSet q where q.userId = :userId and q.status <> :excludedStatus"
          + " and (q.updatedAt > :updatedAt"
          + " or (q.updatedAt = :updatedAt and q.publicId > :publicId))")
  long countVisibleBeforeFocus(
      @Param("userId") long userId,
      @Param("excludedStatus") QuizSetStatus excludedStatus,
      @Param("updatedAt") java.time.Instant updatedAt,
      @Param("publicId") String publicId);

  Page<QuizSet> findAllByUserIdAndQuizTitleContainingIgnoreCase(
      long userId, String quizTitle, Pageable pageable);

  Page<QuizSet> findAllByUserIdAndStatusNotAndQuizTitleContainingIgnoreCase(
      long userId, QuizSetStatus status, String quizTitle, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from QuizSet q where q.status = :status and q.createdAt < :startupAt")
  List<QuizSet> findInterruptedForUpdate(
      @Param("status") QuizSetStatus status, @Param("startupAt") Instant startupAt);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from QuizSet q where q.status = :status and q.generationStartedAt < :cutoff")
  List<QuizSet> findStaleForUpdate(
      @Param("status") QuizSetStatus status, @Param("cutoff") Instant cutoff);

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
