package com.openmd.server.quiz.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizAttemptQuestion;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.dto.response.EssayAssessmentResult;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptQuestionRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSubmittedAnswerRepository;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class EssayAssessmentService {
  private final QuizQuestionRepository questions;
  private final QuizAttemptQuestionRepository attemptQuestions;
  private final QuizSubmittedAnswerRepository answers;
  private final QuizAttemptLockService locks;

  public EssayAssessmentService(
      QuizQuestionRepository questions,
      QuizAttemptQuestionRepository attemptQuestions,
      QuizSubmittedAnswerRepository answers,
      QuizAttemptLockService locks) {
    this.questions = questions;
    this.attemptQuestions = attemptQuestions;
    this.answers = answers;
    this.locks = locks;
  }

  @Transactional
  public EssayAssessmentResult assessMain(
      long userId, String attemptId, String questionId, String requestedAssessment) {
    return assess(userId, attemptId, questionId, requestedAssessment, QuizAttemptType.MAIN);
  }

  @Transactional
  public EssayAssessmentResult assessReview(
      long userId, String attemptId, String questionId, String requestedAssessment) {
    return assess(userId, attemptId, questionId, requestedAssessment, QuizAttemptType.REVIEW);
  }

  private EssayAssessmentResult assess(
      long userId,
      String attemptId,
      String questionId,
      String requestedAssessment,
      QuizAttemptType expectedType) {
    GradingOutcome outcome = parse(requestedAssessment);
    QuizAttempt attempt =
        expectedType == QuizAttemptType.MAIN
            ? locks.lockMain(userId, attemptId)
            : locks.lockReviewAfterSourceMain(userId, attemptId);
    QuizQuestion question =
        questions
            .findByPublicIdAndQuizSetId(questionId, attempt.getQuizSetId())
            .orElseThrow(this::notFound);
    QuizAttemptQuestion attemptQuestion =
        attemptQuestions
            .findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
            .orElseThrow(this::notFound);
    if (question.getType() != QuestionType.ESSAY
        || !answers.existsByAttemptQuestionId(attemptQuestion.getId())) {
      throw new BusinessException(QuizErrorCode.ATTEMPT_CONFLICT);
    }

    if (attemptQuestion.getFinalGradingResult() != null) {
      if (attemptQuestion.getFinalGradingResult() != outcome) {
        throw assessmentConflict(expectedType);
      }
      return response(attempt, questionId, outcome);
    }
    if (attempt.getStatus() != QuizAttemptStatus.SELF_ASSESSMENT_REQUIRED) {
      throw assessmentConflict(expectedType);
    }

    attemptQuestion.selfAssess(outcome);
    Instant now = Instant.now();
    if (attempt.getType() == QuizAttemptType.REVIEW
        && outcome == GradingOutcome.CORRECT
        && attemptQuestion.getSourceAttemptQuestionId() != null) {
      attemptQuestions
          .findById(attemptQuestion.getSourceAttemptQuestionId())
          .orElseThrow()
          .resolveReview(now);
    }
    if (remaining(attempt) == 0) attempt.complete(now);
    return response(attempt, questionId, outcome);
  }

  private EssayAssessmentResult response(
      QuizAttempt attempt, String questionId, GradingOutcome outcome) {
    return new EssayAssessmentResult(
        attempt.getPublicId(), questionId, outcome, attempt.getStatus(), remaining(attempt));
  }

  private int remaining(QuizAttempt attempt) {
    return (int)
        attemptQuestions.findAllByAttemptIdOrderBySequenceNumber(attempt.getId()).stream()
            .filter(question -> question.getFinalGradingResult() == null)
            .count();
  }

  private GradingOutcome parse(String value) {
    try {
      return GradingOutcome.valueOf(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private BusinessException notFound() {
    return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
  }

  private BusinessException assessmentConflict(QuizAttemptType type) {
    return new BusinessException(
        type == QuizAttemptType.REVIEW
            ? QuizErrorCode.REVIEW_UNAVAILABLE
            : QuizErrorCode.ATTEMPT_CONFLICT);
  }
}
