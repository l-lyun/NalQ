package com.openmd.server.learningmaterial.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.dto.command.CreateLearningMaterialCommand;
import com.openmd.server.learningmaterial.dto.model.NewLearningMaterial;
import com.openmd.server.learningmaterial.dto.model.StoredLearningMaterial;
import com.openmd.server.learningmaterial.dto.response.CreatedLearningMaterial;
import com.openmd.server.learningmaterial.error.LearningMaterialErrorCode;
import com.openmd.server.learningmaterial.repository.LearningMaterialCreationStore;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LearningMaterialServiceTest {

	private final LearningMaterialCreationStore store = mock(LearningMaterialCreationStore.class);
	private final UserRepository users = mock(UserRepository.class);
	private final LearningMaterialService service = new LearningMaterialService(store, users);

	@BeforeEach
	void authenticatedUserExists() {
		User user = User.pending("learner@example.com", "learner@example.com", "hash");
		user.activate(Instant.parse("2026-08-20T00:00:00Z"));
		when(users.findById(7L)).thenReturn(Optional.of(user));
	}

	@Test
	void trimsUnicodeTitlePreservesContentAndCountsCodePoints() {
		when(store.create(any())).thenAnswer(invocation -> {
			NewLearningMaterial input = invocation.getArgument(0);
			return new StoredLearningMaterial(
				31L, input.userId(), input.title(), input.content(), input.sourceType(),
				input.requestFingerprint(), Instant.parse("2026-08-20T01:02:03Z")
			);
		});

		CreatedLearningMaterial created = service.create(
			7L,
			"request-1",
			new CreateLearningMaterialCommand("\u2003운영체제\u00A0", " 앞😀\n뒤 ", "NOTION")
		);

		assertEquals("31", created.materialId());
		assertEquals("운영체제", created.title());
		assertEquals(6, created.contentLength());
		assertEquals(ContentEditStatus.EDITABLE, created.contentEditStatus());
		ArgumentCaptor<NewLearningMaterial> captor = ArgumentCaptor.forClass(NewLearningMaterial.class);
		org.mockito.Mockito.verify(store).create(captor.capture());
		assertEquals(" 앞😀\n뒤 ", captor.getValue().content());
		assertEquals(SourceType.NOTION, captor.getValue().sourceType());
	}

	@Test
	void acceptsCodePointLimitsAndRejectsTheNextCodePoint() {
		when(store.create(any())).thenAnswer(invocation -> stored(invocation.getArgument(0)));

		service.create(7L, "title-limit", new CreateLearningMaterialCommand("😀".repeat(255), "본문", "PASTE"));
		BusinessException titleTooLong = assertThrows(BusinessException.class, () -> service.create(
			7L, "title-too-long", new CreateLearningMaterialCommand("😀".repeat(256), "본문", "PASTE")
		));
		assertEquals(CommonErrorCode.INVALID_INPUT, titleTooLong.getErrorCode());
		assertEquals("title", titleTooLong.getFields().getFirst().field());

		service.create(7L, "content-limit", new CreateLearningMaterialCommand("제목", "😀".repeat(20_000), "PASTE"));
		BusinessException contentTooLong = assertThrows(BusinessException.class, () -> service.create(
			7L, "content-too-long", new CreateLearningMaterialCommand("제목", "😀".repeat(20_001), "PASTE")
		));
		assertEquals(LearningMaterialErrorCode.CONTENT_TOO_LONG, contentTooLong.getErrorCode());
	}

	@Test
	void rejectsBlankFieldsUnknownSourceAndInvalidIdempotencyKeyWithFieldErrors() {
		assertInvalidField("title", () -> service.create(
			7L, "key-1", new CreateLearningMaterialCommand("\u2003\u00A0", "본문", "PASTE")
		));
		assertInvalidField("content", () -> service.create(
			7L, "key-2", new CreateLearningMaterialCommand("제목", "\n\t\u00A0", "PASTE")
		));
		assertInvalidField("sourceType", () -> service.create(
			7L, "key-3", new CreateLearningMaterialCommand("제목", "본문", "notion")
		));
		assertInvalidField("Idempotency-Key", () -> service.create(
			7L, "contains space", new CreateLearningMaterialCommand("제목", "본문", "PASTE")
		));
		assertInvalidField("Idempotency-Key", () -> service.create(
			7L, "a".repeat(129), new CreateLearningMaterialCommand("제목", "본문", "PASTE")
		));
	}

	@Test
	void replaysTheStoredResultForTheSameSemanticPayloadAndRejectsAnotherPayload() {
		ArgumentCaptor<NewLearningMaterial> captor = ArgumentCaptor.forClass(NewLearningMaterial.class);
		when(store.create(captor.capture())).thenAnswer(invocation -> stored(invocation.getArgument(0)));

		CreatedLearningMaterial first = service.create(
			7L, "same-key", new CreateLearningMaterialCommand(" 제목 ", "본문", "PASTE")
		);
		CreatedLearningMaterial replay = service.create(
			7L, "same-key", new CreateLearningMaterialCommand("제목", "본문", "PASTE")
		);

		assertEquals(first, replay);
		assertArrayEquals(captor.getAllValues().get(0).idempotencyKeyHash(), captor.getAllValues().get(1).idempotencyKeyHash());
		assertArrayEquals(captor.getAllValues().get(0).requestFingerprint(), captor.getAllValues().get(1).requestFingerprint());

		org.mockito.Mockito.reset(store);
		when(store.create(any())).thenAnswer(invocation -> {
			NewLearningMaterial requested = invocation.getArgument(0);
			return new StoredLearningMaterial(
				31L, requested.userId(), "제목", "원래 본문", SourceType.PASTE,
				new byte[32], Instant.parse("2026-08-20T01:02:03Z")
			);
		});
		BusinessException conflict = assertThrows(BusinessException.class, () -> service.create(
			7L, "same-key", new CreateLearningMaterialCommand("제목", "다른 본문", "PASTE")
		));
		assertEquals(CommonErrorCode.INVALID_INPUT, conflict.getErrorCode());
		assertEquals("Idempotency-Key", conflict.getFields().getFirst().field());
	}

	@Test
	void replayKeepsTheOriginalCreationResponseAfterTheMaterialWasEdited() {
		when(store.create(any())).thenAnswer(invocation -> {
			NewLearningMaterial requested = invocation.getArgument(0);
			return new StoredLearningMaterial(
				31L,
				requested.userId(),
				"수정된 제목",
				"수정 뒤에는 더 길어진 본문",
				requested.sourceType(),
				requested.requestFingerprint(),
				Instant.parse("2026-08-20T01:02:03Z")
			);
		});

		CreatedLearningMaterial replay = service.create(
			7L,
			"same-key",
			new CreateLearningMaterialCommand("  원래 제목  ", "원문😀", "PASTE")
		);

		assertEquals("원래 제목", replay.title());
		assertEquals(3, replay.contentLength());
	}

	private StoredLearningMaterial stored(NewLearningMaterial input) {
		return new StoredLearningMaterial(
			31L, input.userId(), input.title(), input.content(), input.sourceType(),
			input.requestFingerprint(), Instant.parse("2026-08-20T01:02:03Z")
		);
	}

	private void assertInvalidField(String field, org.junit.jupiter.api.function.Executable executable) {
		BusinessException exception = assertThrows(BusinessException.class, executable);
		assertEquals(CommonErrorCode.INVALID_INPUT, exception.getErrorCode());
		assertEquals(field, exception.getFields().getFirst().field());
	}
}
