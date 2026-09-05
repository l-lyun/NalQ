package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.type.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt extends BaseEntity {
  @Column(name = "public_id", nullable = false, updatable = false, length = 36, unique = true)
  private String publicId;

  @Column(name = "quiz_set_id", nullable = false, updatable = false)
  private long quizSetId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private long userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "attempt_type", nullable = false, updatable = false, length = 16)
  private QuizAttemptType type;

  @Column(name = "source_attempt_id", updatable = false)
  private Long sourceAttemptId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private QuizAttemptStatus status;

  @Column(name = "submitted_at")
  private Instant submittedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected QuizAttempt() {}

  public static QuizAttempt main(String publicId, long quizSetId, long userId) {
    QuizAttempt a = new QuizAttempt();
    a.publicId = publicId;
    a.quizSetId = quizSetId;
    a.userId = userId;
    a.type = QuizAttemptType.MAIN;
    a.status = QuizAttemptStatus.IN_PROGRESS;
    return a;
  }

  public static QuizAttempt review(long quizSetId, long userId, long sourceAttemptId) {
    QuizAttempt a = new QuizAttempt();
    a.publicId = UUID.randomUUID().toString();
    a.quizSetId = quizSetId;
    a.userId = userId;
    a.type = QuizAttemptType.REVIEW;
    a.sourceAttemptId = sourceAttemptId;
    a.status = QuizAttemptStatus.IN_PROGRESS;
    return a;
  }

  public void submitted(boolean assessmentRequired, Instant now) {
    submittedAt = now;
    status =
        assessmentRequired
            ? QuizAttemptStatus.SELF_ASSESSMENT_REQUIRED
            : QuizAttemptStatus.COMPLETED;
    if (!assessmentRequired) completedAt = now;
  }

  public void complete(Instant now) {
    status = QuizAttemptStatus.COMPLETED;
    completedAt = now;
  }

  public String getPublicId() {
    return publicId;
  }

  public long getQuizSetId() {
    return quizSetId;
  }

  public long getUserId() {
    return userId;
  }

  public QuizAttemptType getType() {
    return type;
  }

  public Long getSourceAttemptId() {
    return sourceAttemptId;
  }

  public QuizAttemptStatus getStatus() {
    return status;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
