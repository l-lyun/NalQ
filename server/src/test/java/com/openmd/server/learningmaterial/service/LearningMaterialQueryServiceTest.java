package com.openmd.server.learningmaterial.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialDetail;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialPage;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class LearningMaterialQueryServiceTest {

	private final LearningMaterialRepository materials = mock(LearningMaterialRepository.class);
	private final QuizSetRepository quizSets = mock(QuizSetRepository.class);
	private final LearningMaterialQueryService service = new LearningMaterialQueryService(materials, quizSets);

	@Test
	void listsAnOwnedPageWithTrimmedUnicodeQueryAndCalculatedGenerationLocks() {
		LearningMaterial first = material(7L, "운영체제", "본문", SourceType.PASTE, 31L,
			Instant.parse("2026-08-26T01:00:00Z"), Instant.parse("2026-08-26T02:00:00Z"));
		LearningMaterial second = material(7L, "네트워크", "내용", SourceType.NOTION, 30L,
			Instant.parse("2026-08-25T01:00:00Z"), Instant.parse("2026-08-25T02:00:00Z"));
		when(materials.findAllByUserIdAndTitleContaining(org.mockito.ArgumentMatchers.eq(7L),
			org.mockito.ArgumentMatchers.eq("운영"), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(first, second), org.springframework.data.domain.PageRequest.of(1, 6), 13));
		when(quizSets.findLearningMaterialIdsByUserIdAndStatusAndLearningMaterialIdIn(
			7L, QuizSetStatus.GENERATING, List.of(31L, 30L)))
			.thenReturn(List.of(31L));

		LearningMaterialPage result = service.list(7L, 2, 6, "\u2003운영\u00a0");

		assertEquals(2, result.page());
		assertEquals(6, result.size());
		assertEquals(13, result.totalElements());
		assertEquals(3, result.totalPages());
		assertEquals(ContentEditStatus.LOCKED_GENERATING, result.items().getFirst().contentEditStatus());
		assertEquals(ContentEditStatus.EDITABLE, result.items().get(1).contentEditStatus());
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(materials).findAllByUserIdAndTitleContaining(
			org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("운영"), pageable.capture());
		assertEquals(1, pageable.getValue().getPageNumber());
		assertEquals("updatedAt: DESC,id: DESC", pageable.getValue().getSort().toString());
	}

	@Test
	void treatsBlankQueryAsNoFilterAndKeepsOutOfRangePositivePageAggregation() {
		when(materials.findAllByUserId(org.mockito.ArgumentMatchers.eq(7L), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(9, 6), 13));

		LearningMaterialPage result = service.list(7L, 10, 6, "\n\u00a0");

		assertEquals(List.of(), result.items());
		assertEquals(10, result.page());
		assertEquals(13, result.totalElements());
		assertEquals(3, result.totalPages());
	}

	@Test
	void rejectsInvalidPageAndSize() {
		assertInvalidField("page", () -> service.list(7L, 0, 6, null));
		assertInvalidField("size", () -> service.list(7L, 1, 0, null));
		assertInvalidField("size", () -> service.list(7L, 1, 21, null));
	}

	@Test
	void returnsOwnedDetailWithCodePointLengthAndCalculatedLock() {
		LearningMaterial material = material(7L, "운영체제", "본문😀", SourceType.PASTE, 31L,
			Instant.parse("2026-08-20T01:00:00Z"), Instant.parse("2026-08-26T01:00:00Z"));
		when(materials.findByIdAndUserId(31L, 7L)).thenReturn(Optional.of(material));
		when(quizSets.existsByLearningMaterialIdAndUserIdAndStatus(31L, 7L, QuizSetStatus.GENERATING))
			.thenReturn(true);

		LearningMaterialDetail result = service.detail(7L, 31L);

		assertEquals("31", result.materialId());
		assertEquals("본문😀", result.content());
		assertEquals(3, result.contentLength());
		assertEquals(ContentEditStatus.LOCKED_GENERATING, result.contentEditStatus());
	}

	@Test
	void hidesMissingAndOtherOwnersMaterialsAsCommon003AndRejectsNonPositiveIds() {
		when(materials.findByIdAndUserId(31L, 7L)).thenReturn(Optional.empty());
		BusinessException missing = assertThrows(BusinessException.class, () -> service.detail(7L, 31L));
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, missing.getErrorCode());
		assertInvalidField("materialId", () -> service.detail(7L, 0L));
	}

	private void assertInvalidField(String field, org.junit.jupiter.api.function.Executable executable) {
		BusinessException exception = assertThrows(BusinessException.class, executable);
		assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
		assertEquals(field, exception.getFields().getFirst().field());
	}

	private LearningMaterial material(
		long userId,
		String title,
		String content,
		SourceType sourceType,
		long id,
		Instant createdAt,
		Instant updatedAt
	) {
		LearningMaterial material = LearningMaterial.create(
			userId, title, content, sourceType, new byte[32], new byte[32]);
		setField(com.openmd.server.global.entity.BaseEntity.class, material, "id", id);
		setField(com.openmd.server.global.entity.BaseTimeEntity.class, material, "createdAt", createdAt);
		setField(com.openmd.server.global.entity.BaseTimeEntity.class, material, "updatedAt", updatedAt);
		return material;
	}

	private void setField(Class<?> owner, Object target, String name, Object value) {
		try {
			java.lang.reflect.Field field = owner.getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
