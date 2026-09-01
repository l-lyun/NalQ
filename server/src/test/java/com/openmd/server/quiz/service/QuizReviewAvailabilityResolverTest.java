package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QuizReviewAvailabilityResolverTest {

  private final QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
  private final QuizReviewAvailabilityResolver resolver =
      new QuizReviewAvailabilityResolver(attempts);

  @Test
  void allowsReviewOnlyForTheLatestCompletedMainAttemptInTheQuizSet() {
    QuizAttempt requested = completedMain(11L, 21L);
    when(attempts.findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
            21L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.of(requested));

    assertTrue(resolver.isAvailable(7L, requested, 2));
  }

  @Test
  void rejectsAnOlderCompletedMainAttemptEvenWhenItStillHasReviewQuestions() {
    QuizAttempt requested = completedMain(11L, 21L);
    QuizAttempt latest = completedMain(12L, 21L);
    when(attempts.findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
            21L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.of(latest));

    assertFalse(resolver.isAvailable(7L, requested, 2));
  }

  @Test
  void keepsAnExistingActiveReviewReachableWhenNoCurrentQuestionsRemain() {
    QuizAttempt requested = completedMain(11L, 21L);
    QuizAttempt activeReview = mock(QuizAttempt.class);
    when(attempts.findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
            21L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.of(requested));
    when(attempts.findFirstByUserIdAndSourceAttemptIdAndStatusNotOrderByCreatedAtDesc(
            7L, 11L, QuizAttemptStatus.COMPLETED))
        .thenReturn(Optional.of(activeReview));

    assertTrue(resolver.isAvailable(7L, requested, 0));
  }

  private QuizAttempt completedMain(long id, long quizSetId) {
    QuizAttempt attempt = mock(QuizAttempt.class);
    when(attempt.getId()).thenReturn(id);
    when(attempt.getQuizSetId()).thenReturn(quizSetId);
    when(attempt.getType()).thenReturn(QuizAttemptType.MAIN);
    when(attempt.getStatus()).thenReturn(QuizAttemptStatus.COMPLETED);
    return attempt;
  }
}
