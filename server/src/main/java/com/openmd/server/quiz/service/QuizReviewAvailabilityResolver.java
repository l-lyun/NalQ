package com.openmd.server.quiz.service;

import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizReviewAvailabilityResolver {

  private final QuizAttemptRepository attempts;

  public QuizReviewAvailabilityResolver(QuizAttemptRepository attempts) {
    this.attempts = attempts;
  }

  public boolean isAvailable(long userId, QuizAttempt attempt, int reviewQuestionCount) {
    if (attempt.getType() != QuizAttemptType.MAIN
        || attempt.getStatus() != QuizAttemptStatus.COMPLETED) {
      return false;
    }
    QuizAttempt latest =
        attempts
            .findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
                attempt.getQuizSetId(),
                userId,
                QuizAttemptType.MAIN,
                QuizAttemptStatus.COMPLETED)
            .orElse(null);
    if (latest == null || !latest.getId().equals(attempt.getId())) return false;
    if (reviewQuestionCount > 0) return true;
    return attempts
        .findFirstByUserIdAndSourceAttemptIdAndStatusNotOrderByCreatedAtDesc(
            userId, attempt.getId(), QuizAttemptStatus.COMPLETED)
        .isPresent();
  }

  public QuizAttemptResult enrich(long userId, QuizAttempt attempt, QuizAttemptResult result) {
    boolean reviewAvailable =
        isAvailable(userId, attempt, result.summary().reviewQuestionCount());
    return new QuizAttemptResult(
        result.attemptId(),
        result.quizSetId(),
        result.status(),
        reviewAvailable,
        result.summary(),
        result.questionResults());
  }
}
