package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

  Optional<QuizAttempt> findByPublicId(String publicId);

  Optional<QuizAttempt> findByPublicIdAndUserId(String publicId, long userId);

  Optional<QuizAttempt> findFirstByUserIdAndTypeAndStatusOrderByCompletedAtDesc(
      long userId, QuizAttemptType type, QuizAttemptStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<QuizAttempt> findTopByUserIdAndTypeAndStatusOrderByCompletedAtDesc(
      long userId, QuizAttemptType type, QuizAttemptStatus status);

  long countByQuizSetIdAndUserIdAndTypeAndStatus(
      long quizSetId, long userId, QuizAttemptType type, QuizAttemptStatus status);

  Optional<QuizAttempt> findFirstByUserIdAndSourceAttemptIdAndStatusNotOrderByCreatedAtDesc(
      long userId, long sourceAttemptId, QuizAttemptStatus status);

  Optional<QuizAttempt> findFirstByQuizSetIdAndUserIdAndTypeAndStatus(
      long quizSetId, long userId, QuizAttemptType type, QuizAttemptStatus status);

  Optional<QuizAttempt> findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDesc(
      long quizSetId, long userId, QuizAttemptType type, QuizAttemptStatus status);

  Optional<QuizAttempt> findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByUpdatedAtDesc(
      long quizSetId, long userId, QuizAttemptType type, QuizAttemptStatus status);

  Optional<QuizAttempt> findFirstByQuizSetIdAndUserIdAndTypeAndStatusNotOrderByUpdatedAtDesc(
      long quizSetId, long userId, QuizAttemptType type, QuizAttemptStatus status);

  Optional<QuizAttempt> findFirstByQuizSetIdAndUserIdOrderByUpdatedAtDesc(
      long quizSetId, long userId);

  List<QuizAttempt> findAllByQuizSetIdInAndUserId(
      Collection<Long> quizSetIds, long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from QuizAttempt a where a.id = :id and a.userId = :userId")
  Optional<QuizAttempt> findByIdAndUserIdForUpdate(
      @Param("id") long id, @Param("userId") long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from QuizAttempt a where a.publicId = :publicId and a.userId = :userId")
  Optional<QuizAttempt> findOwnedForUpdate(
      @Param("publicId") String publicId, @Param("userId") long userId);
}
