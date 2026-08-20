package com.openmd.server.learningmaterial.application;

import com.openmd.server.auth.domain.AuthErrorCode;
import com.openmd.server.auth.domain.UserRepository;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.LearningMaterialErrorCode;
import com.openmd.server.learningmaterial.domain.SourceType;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "openmd.learning-material.enabled", havingValue = "true", matchIfMissing = true)
public class LearningMaterialService {

	private static final int MAX_TITLE_CODE_POINTS = 255;
	private static final int MAX_CONTENT_CODE_POINTS = 20_000;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

	private final LearningMaterialCreationStore store;
	private final UserRepository users;

	public LearningMaterialService(LearningMaterialCreationStore store, UserRepository users) {
		this.store = store;
		this.users = users;
	}

	public CreatedLearningMaterial create(
		long userId,
		String idempotencyKey,
		CreateLearningMaterialCommand command
	) {
		validateIdempotencyKey(idempotencyKey);
		if (command == null) {
			throw invalid("body", "요청 본문이 필요합니다.");
		}

		String title = trimUnicodeWhitespace(command.title());
		if (title.isEmpty()) {
			throw invalid("title", "제목을 입력해야 합니다.");
		}
		if (codePointCount(title) > MAX_TITLE_CODE_POINTS) {
			throw invalid("title", "제목은 255자를 초과할 수 없습니다.");
		}

		String content = command.content();
		if (content == null || isUnicodeBlank(content)) {
			throw invalid("content", "본문을 입력해야 합니다.");
		}
		if (codePointCount(content) > MAX_CONTENT_CODE_POINTS) {
			throw new BusinessException(
				LearningMaterialErrorCode.CONTENT_TOO_LONG,
				List.of(new FieldError("content", "본문은 20,000자를 초과할 수 없습니다."))
			);
		}

		SourceType sourceType = parseSourceType(command.sourceType());
		users.findById(userId)
			.filter(user -> user.getStatus() == UserStatus.ACTIVE)
			.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIAL));

		byte[] keyHash = sha256(idempotencyKey.getBytes(StandardCharsets.US_ASCII));
		byte[] fingerprint = fingerprint(title, content, sourceType);
		StoredLearningMaterial stored = store.create(new NewLearningMaterial(
			userId, title, content, sourceType, keyHash, fingerprint
		));

		if (!Arrays.equals(fingerprint, stored.requestFingerprint())) {
			throw invalid("Idempotency-Key", "같은 멱등 키가 다른 요청에 이미 사용되었습니다.");
		}
		return new CreatedLearningMaterial(
			Long.toString(stored.id()),
			stored.title(),
			codePointCount(stored.content()),
			stored.contentEditStatus(),
			stored.createdAt()
		);
	}

	private void validateIdempotencyKey(String key) {
		if (key == null || key.isEmpty() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw invalid("Idempotency-Key", "멱등 키는 출력 가능한 ASCII 1~128자여야 합니다.");
		}
		for (int index = 0; index < key.length(); index++) {
			char character = key.charAt(index);
			if (character < 0x21 || character > 0x7e) {
				throw invalid("Idempotency-Key", "멱등 키에는 공백 없는 출력 가능한 ASCII만 사용할 수 있습니다.");
			}
		}
	}

	private SourceType parseSourceType(String value) {
		try {
			return SourceType.valueOf(value);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw invalid("sourceType", "sourceType은 PASTE 또는 NOTION이어야 합니다.");
		}
	}

	private String trimUnicodeWhitespace(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		int start = 0;
		int end = value.length();
		while (start < end) {
			int codePoint = value.codePointAt(start);
			if (!isUnicodeWhitespace(codePoint)) {
				break;
			}
			start += Character.charCount(codePoint);
		}
		while (end > start) {
			int codePoint = value.codePointBefore(end);
			if (!isUnicodeWhitespace(codePoint)) {
				break;
			}
			end -= Character.charCount(codePoint);
		}
		return value.substring(start, end);
	}

	private boolean isUnicodeBlank(String value) {
		return value.codePoints().allMatch(this::isUnicodeWhitespace);
	}

	private boolean isUnicodeWhitespace(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
	}

	private int codePointCount(String value) {
		return value.codePointCount(0, value.length());
	}

	private byte[] fingerprint(String title, String content, SourceType sourceType) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream framed = new DataOutputStream(bytes)) {
				writeFramed(framed, title);
				writeFramed(framed, content);
				writeFramed(framed, sourceType.name());
			}
			return sha256(bytes.toByteArray());
		} catch (IOException exception) {
			throw new IllegalStateException("In-memory fingerprint framing failed", exception);
		}
	}

	private void writeFramed(DataOutputStream output, String value) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(bytes.length);
		output.write(bytes);
	}

	private byte[] sha256(byte[] value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
		}
	}

	private BusinessException invalid(String field, String reason) {
		return new BusinessException(
			CommonErrorCode.INVALID_INPUT,
			List.of(new FieldError(field, reason))
		);
	}
}
