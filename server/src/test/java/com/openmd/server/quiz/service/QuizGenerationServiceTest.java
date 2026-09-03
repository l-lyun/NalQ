package com.openmd.server.quiz.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import com.openmd.server.quiz.dto.command.QuizGenerationConfig;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizGenerationServiceTest {

  @Test
  void rejectsBeforeDatabaseAccessWhenAllWorkerAndQueueSlotsAreOccupied() {
    LearningMaterialRepository materials = mock(LearningMaterialRepository.class);
    QuizSetRepository quizSets = mock(QuizSetRepository.class);
    QuizGenerationAcceptanceTransaction acceptance =
        mock(QuizGenerationAcceptanceTransaction.class);
    QuizGenerationCapacity capacity = new QuizGenerationCapacity(1);
    capacity.tryAcquire();
    QuizGenerationService service =
        new QuizGenerationService(materials, quizSets, acceptance, capacity);

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () ->
                service.accept(
                    7L,
                    "1",
                    new QuizGenerationConfig(
                        List.of(QuestionType.SHORT_ANSWER), QuizDifficulty.NORMAL, 5)));

    assertEquals(QuizErrorCode.GENERATION_UNAVAILABLE, exception.getErrorCode());
    verifyNoInteractions(materials, quizSets, acceptance);
  }
}
