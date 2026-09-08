package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.character.service.QuizCompletionRewardService;
import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizAttemptQuestion;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.repository.QuizAttemptQuestionRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSubmittedAnswerRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EssayAssessmentRewardTest {

  @Test
  void rewardsOnlyAfterTheLastMainEssaySelfAssessmentCompletes() throws Exception {
    QuizQuestionRepository questions = mock(QuizQuestionRepository.class);
    QuizAttemptQuestionRepository attemptQuestions = mock(QuizAttemptQuestionRepository.class);
    QuizSubmittedAnswerRepository answers = mock(QuizSubmittedAnswerRepository.class);
    QuizAttemptLockService locks = mock(QuizAttemptLockService.class);
    QuizCompletionRewardService rewards = mock(QuizCompletionRewardService.class);
    QuizAttempt attempt =
        QuizAttempt.main("550e8400-e29b-41d4-a716-446655440000", 11L, 7L);
    setId(attempt, 31L);
    attempt.submitted(true, Instant.parse("2026-09-08T00:00:00Z"));
    QuizQuestion question = mock(QuizQuestion.class);
    when(question.getId()).thenReturn(41L);
    when(question.getType()).thenReturn(QuestionType.ESSAY);
    QuizAttemptQuestion attemptQuestion = QuizAttemptQuestion.main(31L, 41L, 1);
    setId(attemptQuestion, 51L);
    when(locks.lockMain(7L, attempt.getPublicId())).thenReturn(attempt);
    when(questions.findByPublicIdAndQuizSetId("question", 11L))
        .thenReturn(Optional.of(question));
    when(attemptQuestions.findByAttemptIdAndQuestionId(31L, 41L))
        .thenReturn(Optional.of(attemptQuestion));
    when(attemptQuestions.findAllByAttemptIdOrderBySequenceNumber(31L))
        .thenReturn(List.of(attemptQuestion));
    when(answers.existsByAttemptQuestionId(51L)).thenReturn(true);
    EssayAssessmentService service =
        new EssayAssessmentService(questions, attemptQuestions, answers, locks, rewards);

    service.assessMain(7L, attempt.getPublicId(), "question", "CORRECT");

    assertEquals(QuizAttemptStatus.COMPLETED, attempt.getStatus());
    verify(rewards).lockActiveAccount(7L);
    verify(rewards).rewardFirstCompletion(7L, 11L, attempt.getPublicId());
  }

  private void setId(Object entity, long id) throws Exception {
    Field field = BaseEntity.class.getDeclaredField("id");
    field.setAccessible(true);
    field.set(entity, id);
  }
}
