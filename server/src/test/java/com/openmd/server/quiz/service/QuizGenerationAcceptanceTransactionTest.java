package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.config.QuizGenerationProperties;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import com.openmd.server.quiz.dto.command.QuizGenerationConfig;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class QuizGenerationAcceptanceTransactionTest {

  @Test
  void rejectsAStaleConfirmedRevisionBeforeCreatingOrPublishingAJob() {
    LearningMaterialRepository materials = mock(LearningMaterialRepository.class);
    QuizSetRepository quizSets = mock(QuizSetRepository.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    LearningMaterial material =
        LearningMaterial.create(
            7L, "자료", "최신 본문", SourceType.PASTE, new byte[32], new byte[32]);
    when(materials.findOwnedForUpdate(31L, 7L)).thenReturn(Optional.of(material));
    QuizGenerationAcceptanceTransaction transaction =
        new QuizGenerationAcceptanceTransaction(materials, quizSets, events, properties());

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () ->
                transaction.accept(
                    7L,
                    31L,
                    "8ae74b792008072a214e1e571fff8b049f283ed70382d8e192518c18231ff2f0",
                    config()));

    assertEquals(QuizErrorCode.CONTENT_REVISION_CONFLICT, exception.getErrorCode());
    verifyNoInteractions(quizSets, events);
  }

  private QuizGenerationConfig config() {
    return new QuizGenerationConfig(
        List.of(QuestionType.ESSAY), QuizDifficulty.NORMAL, 5, null);
  }

  private QuizGenerationProperties properties() {
    return new QuizGenerationProperties(
        "gpt-test",
        "medium",
        Duration.ofSeconds(30),
        0,
        1,
        0,
        Duration.ofMinutes(10),
        "quiz-generation-v1");
  }
}
