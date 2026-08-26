package com.openmd.server.learningmaterial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.dto.command.UpdateLearningMaterialCommand;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialDetail;
import com.openmd.server.learningmaterial.error.LearningMaterialErrorCode;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LearningMaterialUpdateServiceTest {

	private final LearningMaterialRepository materials = mock(LearningMaterialRepository.class);
	private final QuizSetRepository quizSets = mock(QuizSetRepository.class);
	private final LearningMaterialUpdateService service = new LearningMaterialUpdateService(materials, quizSets);
	private LearningMaterial material;

	@BeforeEach
	void setUp() {
		material = LearningMaterial.create(7L, "기존 제목", "기존 본문", SourceType.PASTE, new byte[32], new byte[32]);
		ReflectionTestUtils.setField(material, "id", 31L);
		when(materials.findOwnedForUpdate(31L, 7L)).thenReturn(Optional.of(material));
	}

	@Test
	void updatesOnlySentFieldsAndReturnsTheCurrentDetail() {
		when(quizSets.existsByLearningMaterialIdAndUserIdAndStatus(31L, 7L, QuizSetStatus.GENERATING))
			.thenReturn(false);

		LearningMaterialDetail updated = service.update(
			7L,
			31L,
			new UpdateLearningMaterialCommand(true, "\u2003수정 제목\u00a0", true, " 수정😀\n본문 ")
		);

		assertEquals("31", updated.materialId());
		assertEquals("수정 제목", updated.title());
		assertEquals(" 수정😀\n본문 ", updated.content());
		assertEquals(8, updated.contentLength());
		assertEquals(SourceType.PASTE, updated.sourceType());
		assertEquals(ContentEditStatus.EDITABLE, updated.contentEditStatus());
	}

	@Test
	void permitsTitleOnlyUpdateWhileGenerationIsActive() {
		when(quizSets.existsByLearningMaterialIdAndUserIdAndStatus(31L, 7L, QuizSetStatus.GENERATING))
			.thenReturn(true);

		LearningMaterialDetail updated = service.update(
			7L,
			31L,
			new UpdateLearningMaterialCommand(true, "새 제목", false, null)
		);

		assertEquals("새 제목", updated.title());
		assertEquals("기존 본문", updated.content());
		assertEquals(ContentEditStatus.LOCKED_GENERATING, updated.contentEditStatus());
	}

	@Test
	void rejectsContentUpdateWhileGenerationIsActiveWithoutChangingEitherField() {
		when(quizSets.existsByLearningMaterialIdAndUserIdAndStatus(31L, 7L, QuizSetStatus.GENERATING))
			.thenReturn(true);

		BusinessException exception = assertThrows(BusinessException.class, () -> service.update(
			7L,
			31L,
			new UpdateLearningMaterialCommand(true, "함께 보낸 제목", true, "새 본문")
		));

		assertEquals(LearningMaterialErrorCode.CONTENT_LOCKED_GENERATING, exception.getErrorCode());
		assertEquals("기존 제목", material.getTitle());
		assertEquals("기존 본문", material.getContent());
	}

	@Test
	void validatesPresenceOwnershipAndTheSameTitleContentLimitsAsCreation() {
		assertInvalid("body", new UpdateLearningMaterialCommand(false, null, false, null));
		assertInvalid("title", new UpdateLearningMaterialCommand(true, "\u2003\u00a0", false, null));
		assertInvalid("title", new UpdateLearningMaterialCommand(true, "😀".repeat(256), false, null));
		assertInvalid("content", new UpdateLearningMaterialCommand(false, null, true, "\n\t\u00a0"));

		BusinessException tooLong = assertThrows(BusinessException.class, () -> service.update(
			7L, 31L, new UpdateLearningMaterialCommand(false, null, true, "😀".repeat(20_001))
		));
		assertEquals(LearningMaterialErrorCode.CONTENT_TOO_LONG, tooLong.getErrorCode());

		when(materials.findOwnedForUpdate(32L, 7L)).thenReturn(Optional.empty());
		BusinessException hidden = assertThrows(BusinessException.class, () -> service.update(
			7L, 32L, new UpdateLearningMaterialCommand(true, "제목", false, null)
		));
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, hidden.getErrorCode());
		verify(materials).findOwnedForUpdate(32L, 7L);
	}

	private void assertInvalid(String field, UpdateLearningMaterialCommand command) {
		BusinessException exception = assertThrows(BusinessException.class, () -> service.update(7L, 31L, command));
		assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
		assertEquals(field, exception.getFields().getFirst().field());
	}
}
