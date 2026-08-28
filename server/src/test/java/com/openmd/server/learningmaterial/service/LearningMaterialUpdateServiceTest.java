package com.openmd.server.learningmaterial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.dto.request.UpdateLearningMaterialRequest;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LearningMaterialUpdateServiceTest {

  private final LearningMaterialRepository materials = mock(LearningMaterialRepository.class);
  private final QuizSetRepository quizSets = mock(QuizSetRepository.class);
  private final LearningMaterialUpdateService service =
      new LearningMaterialUpdateService(materials, quizSets);

  @Test
  void titleCanChangeWhileGenerationLocksContent() {
    LearningMaterial material = material();
    when(materials.findOwnedForUpdate(31L, 7L)).thenReturn(Optional.of(material));
    when(quizSets.existsByLearningMaterialIdAndUserIdAndStatus(
            31L, 7L, QuizSetStatus.GENERATING))
        .thenReturn(true);

    var changed = service.update(7L, 31L, new UpdateLearningMaterialRequest(" 새 제목 ", null));

    assertEquals("새 제목", changed.title());
    assertEquals("기존 본문", changed.content());

    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> service.update(7L, 31L, new UpdateLearningMaterialRequest(null, "새 본문")));
    assertEquals("MATERIAL_001", exception.getErrorCode().code());
  }

  @Test
  void emptyPatchIsInvalid() {
    BusinessException exception =
        assertThrows(
            BusinessException.class,
            () -> service.update(7L, 31L, new UpdateLearningMaterialRequest(null, null)));

    assertEquals("COMMON_001", exception.getErrorCode().code());
  }

  private LearningMaterial material() {
    return LearningMaterial.create(
        7L, "기존 제목", "기존 본문", SourceType.PASTE, new byte[32], new byte[32]);
  }
}
