package com.openmd.server.quiz.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptLockService {
  private final QuizAttemptRepository attempts;

  public QuizAttemptLockService(QuizAttemptRepository attempts) {
    this.attempts = attempts;
  }

  public QuizAttempt lockMain(long userId, String attemptId) {
    QuizAttempt main = attempts.findOwnedForUpdate(attemptId, userId).orElseThrow(this::notFound);
    if (main.getType() != QuizAttemptType.MAIN) throw conflict();
    return main;
  }

  public QuizAttempt lockReviewAfterSourceMain(long userId, String reviewId) {
    QuizAttempt observed =
        attempts.findByPublicIdAndUserId(reviewId, userId).orElseThrow(this::notFound);
    if (observed.getType() != QuizAttemptType.REVIEW || observed.getSourceAttemptId() == null) {
      throw conflict();
    }
    QuizAttempt source =
        attempts
            .findByIdAndUserIdForUpdate(observed.getSourceAttemptId(), userId)
            .orElseThrow(this::notFound);
    if (source.getType() != QuizAttemptType.MAIN) throw conflict();
    QuizAttempt review = attempts.findOwnedForUpdate(reviewId, userId).orElseThrow(this::notFound);
    if (review.getType() != QuizAttemptType.REVIEW
        || !observed.getSourceAttemptId().equals(review.getSourceAttemptId())) {
      throw conflict();
    }
    return review;
  }

  private BusinessException notFound() {
    return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
  }

  private BusinessException conflict() {
    return new BusinessException(QuizErrorCode.ATTEMPT_CONFLICT);
  }
}
