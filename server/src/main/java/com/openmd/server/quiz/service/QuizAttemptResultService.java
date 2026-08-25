package com.openmd.server.quiz.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.dto.response.PendingSelfAssessment;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.dto.response.ReviewAttemptResult;
import com.openmd.server.quiz.repository.QuizAttemptQuestionRepository;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSetRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptResultService {

  private final QuizAttemptRepository attempts;
  private final QuizAttemptResultProjector projector;
  private final QuizAttemptQuestionRepository attemptQuestions;
  private final QuizQuestionRepository questions;
  private final QuizSetRepository sets;

  public QuizAttemptResultService(
      QuizAttemptRepository attempts,
      QuizAttemptResultProjector projector,
      QuizAttemptQuestionRepository attemptQuestions,
      QuizQuestionRepository questions,
      QuizSetRepository sets) {
    this.attempts = attempts;
    this.projector = projector;
    this.attemptQuestions = attemptQuestions;
    this.questions = questions;
    this.sets = sets;
  }

  @Transactional(readOnly = true)
  public QuizAttemptResult result(long userId, String attemptPublicId) {
    var attempt =
        attempts
            .findByPublicIdAndUserId(attemptPublicId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    return projector.project(attempt);
  }

  @Transactional(readOnly = true)
  public ReviewAttemptResult reviewResult(long userId, String reviewId) {
    var review =
        attempts
            .findByPublicIdAndUserId(reviewId, userId)
            .filter(a -> a.getType() == com.openmd.server.quiz.domain.type.QuizAttemptType.REVIEW)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    var source =
        attempts
            .findById(review.getSourceAttemptId())
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    var projected = projector.project(review);
    return new ReviewAttemptResult(
        review.getPublicId(),
        source.getPublicId(),
        review.getStatus().name(),
        projected.summary(),
        projected.questionResults());
  }

  @Transactional(readOnly = true)
  public PendingSelfAssessment pending(long userId, String quizSetId) {
    var set =
        sets.findByPublicIdAndUserId(quizSetId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    var attempt =
        attempts
            .findFirstByQuizSetIdAndUserIdAndStatus(
                set.getId(), userId, QuizAttemptStatus.SELF_ASSESSMENT_REQUIRED)
            .orElse(null);
    if (attempt == null) return null;
    var ids =
        attemptQuestions.findAllByAttemptIdOrderBySequenceNumber(attempt.getId()).stream()
            .filter(q -> q.getFinalGradingResult() == null)
            .map(q -> questions.findById(q.getQuestionId()).orElseThrow().getPublicId())
            .toList();
    return new PendingSelfAssessment(
        attempt.getPublicId(), set.getPublicId(), attempt.getStatus(), ids);
  }
}
