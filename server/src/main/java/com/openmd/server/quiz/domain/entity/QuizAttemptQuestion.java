package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.type.*;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "quiz_attempt_questions")
public class QuizAttemptQuestion extends BaseEntity {
  @Column(name = "attempt_id", nullable = false, updatable = false)
  private long attemptId;

  @Column(name = "question_id", nullable = false, updatable = false)
  private long questionId;

  @Column(name = "source_attempt_question_id", updatable = false)
  private Long sourceAttemptQuestionId;

  @Column(name = "sequence_number", nullable = false, updatable = false)
  private int sequenceNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "automatic_grading_result", length = 16)
  private GradingOutcome automaticGradingResult;

  @Enumerated(EnumType.STRING)
  @Column(name = "final_grading_result", length = 16)
  private GradingOutcome finalGradingResult;

  @Enumerated(EnumType.STRING)
  @Column(name = "grading_method", length = 24)
  private GradingMethod gradingMethod;

  @Column(name = "review_resolved_at")
  private Instant reviewResolvedAt;

  protected QuizAttemptQuestion() {}

  public static QuizAttemptQuestion main(long attemptId, long questionId, int sequence) {
    return create(attemptId, questionId, null, sequence);
  }

  public static QuizAttemptQuestion review(
      long attemptId, long questionId, long sourceId, int sequence) {
    return create(attemptId, questionId, sourceId, sequence);
  }

  private static QuizAttemptQuestion create(
      long attemptId, long questionId, Long sourceId, int sequence) {
    QuizAttemptQuestion q = new QuizAttemptQuestion();
    q.attemptId = attemptId;
    q.questionId = questionId;
    q.sourceAttemptQuestionId = sourceId;
    q.sequenceNumber = sequence;
    return q;
  }

  public void automatic(GradingOutcome outcome) {
    if (outcome == GradingOutcome.PARTIAL) throw new IllegalArgumentException();
    automaticGradingResult = outcome;
    finalGradingResult = outcome;
    gradingMethod = GradingMethod.AUTOMATIC;
  }

  public void selfAssess(GradingOutcome outcome) {
    automaticGradingResult = null;
    finalGradingResult = outcome;
    gradingMethod = GradingMethod.SELF_ASSESSMENT;
  }

  public void override(GradingOutcome outcome) {
    if (outcome == GradingOutcome.PARTIAL || automaticGradingResult == null)
      throw new IllegalArgumentException();
    finalGradingResult = outcome;
    gradingMethod = GradingMethod.USER_OVERRIDE;
  }

  public void resolveReview(Instant now) {
    reviewResolvedAt = now;
  }

  public long getAttemptId() {
    return attemptId;
  }

  public long getQuestionId() {
    return questionId;
  }

  public Long getSourceAttemptQuestionId() {
    return sourceAttemptQuestionId;
  }

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public GradingOutcome getAutomaticGradingResult() {
    return automaticGradingResult;
  }

  public GradingOutcome getFinalGradingResult() {
    return finalGradingResult;
  }

  public GradingMethod getGradingMethod() {
    return gradingMethod;
  }

  public Instant getReviewResolvedAt() {
    return reviewResolvedAt;
  }
}
