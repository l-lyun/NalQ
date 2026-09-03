package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.notification.domain.QuizGenerationNotification;
import com.openmd.server.notification.repository.NotificationRepository;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizSetFailureCode;
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

class QuizGenerationNotificationTest {
  private final QuizSetRepository sets = mock(QuizSetRepository.class);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final QuizGenerationPersistenceService service =
      new QuizGenerationPersistenceService(
          sets,
          mock(QuizQuestionRepository.class),
          mock(QuizQuestionChoiceRepository.class),
          mock(QuizShortAnswerAnswerRepository.class),
          mock(QuizEssayAnswerGuideRepository.class),
          mock(QuizFillInTheBlankRepository.class),
          mock(QuizFillInTheBlankAnswerRepository.class),
          notifications);

  @Test
  void sourceInsufficientFailureCreatesExactlyOneMatchingNotification() {
    QuizSet set = QuizSet.generating(7L, 31L, "운영체제 퀴즈");
    when(sets.findOwnedForUpdate(set.getPublicId(), 7L)).thenReturn(Optional.of(set));

    assertEquals(0, service.complete(7L, set.getPublicId(), List.of(), 10));

    assertEquals(QuizSetFailureCode.SOURCE_INSUFFICIENT, set.getFailureCode());
    verify(notifications).save(any(QuizGenerationNotification.class));
  }

  @Test
  void retryingAnAlreadyFailedWorkerDoesNotCreateAnotherNotification() {
    QuizSet set = QuizSet.generating(7L, 31L, "운영체제 퀴즈");
    set.fail(QuizSetFailureCode.GENERATION_FAILED);
    when(sets.findOwnedForUpdate(set.getPublicId(), 7L)).thenReturn(Optional.of(set));

    service.failGeneration(7L, set.getPublicId());

    verify(notifications, never()).save(any());
  }
}
