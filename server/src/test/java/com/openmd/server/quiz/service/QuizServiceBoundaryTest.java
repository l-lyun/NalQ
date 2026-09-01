package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptQuestionRepository;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizFillInTheBlankAnswerRepository;
import com.openmd.server.quiz.repository.QuizFillInTheBlankRepository;
import com.openmd.server.quiz.repository.QuizQuestionChoiceRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSetRepository;
import com.openmd.server.quiz.repository.QuizShortAnswerAnswerRepository;
import com.openmd.server.quiz.repository.QuizSubmittedAnswerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

class QuizServiceBoundaryTest {

  @Test
  void blocksMainResultProjectionForAReviewAttempt() {
    QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
    QuizAttempt review = mock(QuizAttempt.class);
    when(review.getType()).thenReturn(QuizAttemptType.REVIEW);
    when(attempts.findByPublicIdAndUserId("review", 7L)).thenReturn(Optional.of(review));
    QuizAttemptResultService service =
        new QuizAttemptResultService(
            attempts,
            mock(QuizAttemptResultProjector.class),
            mock(QuizReviewAvailabilityResolver.class),
            mock(QuizAttemptQuestionRepository.class),
            mock(QuizQuestionRepository.class),
            mock(QuizSetRepository.class));

    BusinessException failure =
        assertThrows(BusinessException.class, () -> service.result(7L, "review"));

    assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, failure.getErrorCode());
  }

  @Test
  void blocksReviewResultWhileTheReviewIsStillSolving() {
    QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
    QuizAttempt review = mock(QuizAttempt.class);
    when(review.getType()).thenReturn(QuizAttemptType.REVIEW);
    when(review.getStatus()).thenReturn(QuizAttemptStatus.IN_PROGRESS);
    when(attempts.findByPublicIdAndUserId("review", 7L)).thenReturn(Optional.of(review));
    QuizAttemptResultService service =
        new QuizAttemptResultService(
            attempts,
            mock(QuizAttemptResultProjector.class),
            mock(QuizReviewAvailabilityResolver.class),
            mock(QuizAttemptQuestionRepository.class),
            mock(QuizQuestionRepository.class),
            mock(QuizSetRepository.class));

    BusinessException failure =
        assertThrows(BusinessException.class, () -> service.reviewResult(7L, "review"));

    assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, failure.getErrorCode());
  }

  @Test
  void pendingSelfAssessmentIgnoresReviewAttempts() {
    QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
    QuizSetRepository sets = mock(QuizSetRepository.class);
    QuizSet set = mock(QuizSet.class);
    QuizAttempt review = mock(QuizAttempt.class);
    when(set.getId()).thenReturn(11L);
    when(set.getPublicId()).thenReturn("set");
    when(sets.findByPublicIdAndUserId("set", 7L)).thenReturn(Optional.of(set));
    when(review.getType()).thenReturn(QuizAttemptType.REVIEW);
    when(review.getId()).thenReturn(22L);
    when(review.getPublicId()).thenReturn("review");
    when(review.getStatus()).thenReturn(QuizAttemptStatus.SELF_ASSESSMENT_REQUIRED);
    when(attempts.findFirstByQuizSetIdAndUserIdAndTypeAndStatus(
            11L, 7L, QuizAttemptType.MAIN, QuizAttemptStatus.SELF_ASSESSMENT_REQUIRED))
        .thenReturn(Optional.of(review));
    QuizAttemptResultService service =
        new QuizAttemptResultService(
            attempts,
            mock(QuizAttemptResultProjector.class),
            mock(QuizReviewAvailabilityResolver.class),
            mock(QuizAttemptQuestionRepository.class),
            mock(QuizQuestionRepository.class),
            sets);

    assertNull(service.pending(7L, "set"));
  }

  @Test
  void mapsAnAttemptPublicIdUniqueViolationToAttemptConflict() {
    QuizSetRepository sets = mock(QuizSetRepository.class);
    QuizQuestionRepository questions = mock(QuizQuestionRepository.class);
    QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
    QuizSet set = mock(QuizSet.class);
    when(set.getId()).thenReturn(11L);
    when(set.getStatus()).thenReturn(QuizSetStatus.READY);
    when(sets.findOwnedForUpdate("set", 7L)).thenReturn(Optional.of(set));
    when(attempts.findByPublicId("550e8400-e29b-41d4-a716-446655440000"))
        .thenReturn(Optional.empty());
    when(questions.findAllByQuizSetIdOrderByNumber(11L)).thenReturn(List.of());
    when(attempts.saveAndFlush(org.mockito.ArgumentMatchers.any(QuizAttempt.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate public id"));
    QuizAttemptSubmissionService service =
        new QuizAttemptSubmissionService(
            sets,
            questions,
            attempts,
            mock(QuizAttemptQuestionRepository.class),
            mock(QuizSubmittedAnswerRepository.class),
            mock(QuizQuestionChoiceRepository.class),
            mock(QuizShortAnswerAnswerRepository.class),
            mock(QuizFillInTheBlankRepository.class),
            mock(QuizFillInTheBlankAnswerRepository.class),
            mock(QuizAttemptLockService.class));

    BusinessException failure =
        assertThrows(
            BusinessException.class,
            () -> service.submit(7L, "set", "550e8400-e29b-41d4-a716-446655440000", List.of()));

    assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, failure.getErrorCode());
  }

  @Test
  void locksSourceMainBeforeReviewUsingTheSharedAttemptLockBoundary() {
    QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
    QuizAttempt review = mock(QuizAttempt.class);
    QuizAttempt source = mock(QuizAttempt.class);
    when(review.getType()).thenReturn(QuizAttemptType.REVIEW);
    when(review.getSourceAttemptId()).thenReturn(20L);
    when(source.getType()).thenReturn(QuizAttemptType.MAIN);
    when(attempts.findByPublicIdAndUserId("review", 7L)).thenReturn(Optional.of(review));
    when(attempts.findByIdAndUserIdForUpdate(20L, 7L)).thenReturn(Optional.of(source));
    when(attempts.findOwnedForUpdate("review", 7L)).thenReturn(Optional.of(review));
    QuizAttemptLockService locks = new QuizAttemptLockService(attempts);

    assertSame(review, locks.lockReviewAfterSourceMain(7L, "review"));

    InOrder order = inOrder(attempts);
    order.verify(attempts).findByPublicIdAndUserId("review", 7L);
    order.verify(attempts).findByIdAndUserIdForUpdate(20L, 7L);
    order.verify(attempts).findOwnedForUpdate("review", 7L);
  }
}
