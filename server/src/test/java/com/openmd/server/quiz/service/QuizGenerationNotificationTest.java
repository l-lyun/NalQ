package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.notification.domain.QuizGenerationNotification;
import com.openmd.server.notification.repository.NotificationRepository;
import com.openmd.server.push.service.PushOutboxService;
import com.openmd.server.quiz.domain.QuizGenerationCandidate;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizSetFailureCode;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.repository.QuizEssayAnswerGuideRepository;
import com.openmd.server.quiz.repository.QuizFillInTheBlankAnswerRepository;
import com.openmd.server.quiz.repository.QuizFillInTheBlankRepository;
import com.openmd.server.quiz.repository.QuizQuestionChoiceRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSetRepository;
import com.openmd.server.quiz.repository.QuizShortAnswerAnswerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

class QuizGenerationNotificationTest {
  private final QuizSetRepository sets = mock(QuizSetRepository.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final QuizQuestionRepository questions = mock(QuizQuestionRepository.class);
  private final PushOutboxService pushOutbox = mock(PushOutboxService.class);
  private final QuizGenerationPersistenceService service =
      new QuizGenerationPersistenceService(
          sets,
          questions,
          mock(QuizQuestionChoiceRepository.class),
          mock(QuizShortAnswerAnswerRepository.class),
          mock(QuizEssayAnswerGuideRepository.class),
          mock(QuizFillInTheBlankRepository.class),
          mock(QuizFillInTheBlankAnswerRepository.class),
          notifications,
          pushOutbox);

  @BeforeEach
  void returnSavedNotification() {
    when(notifications.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void terminalNotificationIsFlushedBeforeUsingItsAuditedTimeForDelivery() {
    QuizSet set = QuizSet.generating(7L, 31L, "트랜잭션 퀴즈");
    when(sets.findOwnedForUpdate(set.getPublicId(), 7L)).thenReturn(Optional.of(set));
    service.failGeneration(7L, set.getPublicId());
    verify(notifications).saveAndFlush(any(QuizGenerationNotification.class));
    verify(pushOutbox).enqueue(any(QuizGenerationNotification.class));
  }

  @Test
  void sourceInsufficientFailureCreatesExactlyOneMatchingNotification() {
    QuizSet set = QuizSet.generating(7L, 31L, "운영체제 퀴즈");
    when(sets.findOwnedForUpdate(set.getPublicId(), 7L)).thenReturn(Optional.of(set));

    assertEquals(0, service.complete(7L, set.getPublicId(), List.of(), 10));

    assertEquals(QuizSetFailureCode.SOURCE_INSUFFICIENT, set.getFailureCode());
    verify(notifications).saveAndFlush(any(QuizGenerationNotification.class));
    verify(pushOutbox).enqueue(any(QuizGenerationNotification.class));
  }

  @Test
  void retryingAnAlreadyFailedWorkerDoesNotCreateAnotherNotification() {
    QuizSet set = QuizSet.generating(7L, 31L, "운영체제 퀴즈");
    set.fail(QuizSetFailureCode.GENERATION_FAILED);
    when(sets.findOwnedForUpdate(set.getPublicId(), 7L)).thenReturn(Optional.of(set));

    service.failGeneration(7L, set.getPublicId());

    verify(notifications, never()).saveAndFlush(any());
    org.mockito.Mockito.verifyNoInteractions(pushOutbox);
  }

  @Test
  void fewerThanEightyPercentValidQuestionsFailsWithoutPersistingPartialResults() {
    QuizSet set = QuizSet.generating(7L, 31L, "운영체제 퀴즈");
    when(sets.findOwnedForUpdate(set.getPublicId(), 7L)).thenReturn(Optional.of(set));
    QuizGenerationCandidate oneValidQuestion =
        new QuizGenerationCandidate(
            QuestionType.SHORT_ANSWER,
            "운영체제",
            "프로세스란 무엇인가요?",
            "실행 중인 프로그램입니다.",
            "프로세스는 실행 중인 프로그램이다.",
            List.of(),
            List.of("실행 중인 프로그램"),
            List.of(),
            "",
            List.of());

    assertEquals(
        0,
        service.complete(7L, set.getPublicId(), List.of(oneValidQuestion), 10));

    assertEquals(QuizSetFailureCode.SOURCE_INSUFFICIENT, set.getFailureCode());
    verify(questions, never()).saveAndFlush(any());
    verify(notifications).saveAndFlush(any(QuizGenerationNotification.class));
    verify(pushOutbox).enqueue(any(QuizGenerationNotification.class));
  }

  @Test
  void startupAndStaleRecoveryAlsoEnqueueTheirTerminalNotifications() {
    var interrupted = QuizSet.generating(7L, 31L, "중단 퀴즈");
    var stale = QuizSet.generating(8L, 32L, "지연 퀴즈");
    var boundary = java.time.Instant.parse("2026-09-06T00:00:00Z");
    when(sets.findInterruptedForUpdate(com.openmd.server.quiz.domain.type.QuizSetStatus.GENERATING, boundary))
        .thenReturn(List.of(interrupted));
    when(sets.findStaleForUpdate(com.openmd.server.quiz.domain.type.QuizSetStatus.GENERATING, boundary))
        .thenReturn(List.of(stale));
    assertEquals(1, service.failInterruptedGenerations(boundary));
    assertEquals(List.of(stale.getPublicId()), service.failStaleGenerations(boundary));
    verify(pushOutbox, org.mockito.Mockito.times(2)).enqueue(any());
  }
}
