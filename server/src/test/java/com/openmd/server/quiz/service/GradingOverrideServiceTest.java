package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.global.error.BusinessException;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GradingOverrideServiceTest {

  private final QuizQuestionRepository questions = mock(QuizQuestionRepository.class);
  private final QuizAttemptQuestionRepository attemptQuestions =
      mock(QuizAttemptQuestionRepository.class);
  private final QuizSubmittedAnswerRepository answers = mock(QuizSubmittedAnswerRepository.class);
  private final QuizAttemptResultProjector projector = mock(QuizAttemptResultProjector.class);
  private final QuizReviewAvailabilityResolver reviewAvailability =
      mock(QuizReviewAvailabilityResolver.class);
  private final QuizAttemptLockService locks = mock(QuizAttemptLockService.class);
  private final GradingOverrideService service =
      new GradingOverrideService(
          questions, attemptQuestions, answers, projector, reviewAvailability, locks);

  private QuizAttempt attempt;
  private QuizQuestion question;
  private QuizAttemptQuestion attemptQuestion;

  @BeforeEach
  void setUp() {
    attempt = mock(QuizAttempt.class);
    question = mock(QuizQuestion.class);
    attemptQuestion = mock(QuizAttemptQuestion.class);

    when(locks.lockMain(7L, "attempt_1")).thenReturn(attempt);
    when(attempt.getId()).thenReturn(11L);
    when(attempt.getQuizSetId()).thenReturn(13L);
    when(attempt.getStatus()).thenReturn(QuizAttemptStatus.COMPLETED);
    when(attempt.getType()).thenReturn(QuizAttemptType.MAIN);
    when(question.getId()).thenReturn(17L);
    when(question.getType()).thenReturn(QuestionType.FILL_IN_THE_BLANK);
    when(questions.findByPublicIdAndQuizSetId("question_1", 13L))
        .thenReturn(Optional.of(question));
    when(attemptQuestions.findByAttemptIdAndQuestionId(11L, 17L))
        .thenReturn(Optional.of(attemptQuestion));
    when(attemptQuestion.getId()).thenReturn(19L);
  }

  @Test
  void updatesTheCurrentOutcomeOfAnAnsweredFillInTheBlankQuestion() {
    QuizAttemptResult expected = mock(QuizAttemptResult.class);
    when(answers.existsByAttemptQuestionId(19L)).thenReturn(true);
    when(projector.project(attempt)).thenReturn(expected);
    when(reviewAvailability.enrich(7L, attempt, expected)).thenReturn(expected);

    QuizAttemptResult actual = service.update(7L, "attempt_1", "question_1", "CORRECT");

    assertEquals(expected, actual);
    verify(attemptQuestion).override(GradingOutcome.CORRECT);
    verify(attemptQuestions).flush();
  }

  @Test
  void rejectsACompletelyUnansweredFillInTheBlankQuestion() {
    when(answers.existsByAttemptQuestionId(19L)).thenReturn(false);

    BusinessException error =
        assertThrows(
            BusinessException.class,
            () -> service.update(7L, "attempt_1", "question_1", "CORRECT"));

    assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, error.getErrorCode());
    verify(attemptQuestion, never()).override(GradingOutcome.CORRECT);
  }
}
