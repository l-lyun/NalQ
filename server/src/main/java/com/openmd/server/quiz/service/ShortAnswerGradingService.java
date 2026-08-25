package com.openmd.server.quiz.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.*;
import com.openmd.server.quiz.domain.entity.*;
import com.openmd.server.quiz.domain.type.*;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class ShortAnswerGradingService {
  private final QuizAttemptRepository attempts;
  private final QuizQuestionRepository questions;
  private final QuizAttemptQuestionRepository aqs;
  private final QuizSubmittedAnswerRepository answers;
  private final QuizAttemptResultProjector projector;

  public ShortAnswerGradingService(
      QuizAttemptRepository attempts,
      QuizQuestionRepository questions,
      QuizAttemptQuestionRepository aqs,
      QuizSubmittedAnswerRepository answers,
      QuizAttemptResultProjector projector) {
    this.attempts = attempts;
    this.questions = questions;
    this.aqs = aqs;
    this.answers = answers;
    this.projector = projector;
  }

  @Transactional
  public QuizAttemptResult update(long userId, String attemptId, String questionId, String value) {
    GradingOutcome outcome = parse(value);
    QuizAttempt attempt =
        attempts.findOwnedForUpdate(attemptId, userId).orElseThrow(this::notFound);
    QuizQuestion q =
        questions
            .findByPublicIdAndQuizSetId(questionId, attempt.getQuizSetId())
            .orElseThrow(this::notFound);
    QuizAttemptQuestion aq =
        aqs.findByAttemptIdAndQuestionId(attempt.getId(), q.getId()).orElseThrow(this::notFound);
    if (attempt.getStatus() != QuizAttemptStatus.COMPLETED
        || attempt.getType() != QuizAttemptType.MAIN
        || q.getType() != QuestionType.SHORT_ANSWER
        || !answers.existsByAttemptQuestionId(aq.getId()))
      throw new BusinessException(QuizErrorCode.ATTEMPT_CONFLICT);
    aq.override(outcome);
    aqs.flush();
    return projector.project(attempt);
  }

  private GradingOutcome parse(String value) {
    try {
      GradingOutcome o = GradingOutcome.valueOf(value);
      if (o == GradingOutcome.PARTIAL) throw new IllegalArgumentException();
      return o;
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
