package com.openmd.server.quiz.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizAttemptQuestion;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptQuestionRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSubmittedAnswerRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class GradingOverrideService {
  private final QuizQuestionRepository questions;
  private final QuizAttemptQuestionRepository attemptQuestions;
  private final QuizSubmittedAnswerRepository answers;
  private final QuizAttemptResultProjector projector;
  private final QuizReviewAvailabilityResolver reviewAvailability;
  private final QuizAttemptLockService locks;

  public GradingOverrideService(
      QuizQuestionRepository questions,
      QuizAttemptQuestionRepository attemptQuestions,
      QuizSubmittedAnswerRepository answers,
      QuizAttemptResultProjector projector,
      QuizReviewAvailabilityResolver reviewAvailability,
      QuizAttemptLockService locks) {
    this.questions = questions;
    this.attemptQuestions = attemptQuestions;
    this.answers = answers;
    this.projector = projector;
    this.reviewAvailability = reviewAvailability;
    this.locks = locks;
  }

  @Transactional
  public QuizAttemptResult update(long userId, String attemptId, String questionId, String value) {
    GradingOutcome outcome = parse(value);
    QuizAttempt attempt = locks.lockMain(userId, attemptId);
    QuizQuestion question =
        questions
            .findByPublicIdAndQuizSetId(questionId, attempt.getQuizSetId())
            .orElseThrow(this::notFound);
    QuizAttemptQuestion attemptQuestion =
        attemptQuestions
            .findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
            .orElseThrow(this::notFound);
    if (attempt.getStatus() != QuizAttemptStatus.COMPLETED
        || attempt.getType() != QuizAttemptType.MAIN
        || !isUserCorrectable(question.getType())
        || !answers.existsByAttemptQuestionId(attemptQuestion.getId())) {
      throw new BusinessException(QuizErrorCode.ATTEMPT_CONFLICT);
    }
    attemptQuestion.override(outcome);
    attemptQuestions.flush();
    return reviewAvailability.enrich(userId, attempt, projector.project(attempt));
  }

  private boolean isUserCorrectable(QuestionType type) {
    return type == QuestionType.SHORT_ANSWER || type == QuestionType.FILL_IN_THE_BLANK;
  }

  private GradingOutcome parse(String value) {
    try {
      GradingOutcome outcome = GradingOutcome.valueOf(value);
      if (outcome == GradingOutcome.PARTIAL) throw new IllegalArgumentException();
      return outcome;
    } catch (Exception e) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT,
          List.of(new FieldError("outcome", "outcome은 CORRECT 또는 INCORRECT여야 합니다.")));
    }
  }

  private BusinessException notFound() {
    return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
  }
}
