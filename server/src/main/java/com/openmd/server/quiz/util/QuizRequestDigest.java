package com.openmd.server.quiz.util;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

public final class QuizRequestDigest {

	private QuizRequestDigest() {
	}

	public static byte[] idempotencyKey(String key) {
		if (key == null || key.isEmpty() || key.length() > 128) {
			throw invalid("Idempotency-Key", "멱등 키는 출력 가능한 ASCII 1~128자여야 합니다.");
		}
		for (int index = 0; index < key.length(); index++) {
			char character = key.charAt(index);
			if (character < 0x21 || character > 0x7e) {
				throw invalid("Idempotency-Key", "멱등 키에는 공백 없는 출력 가능한 ASCII만 사용할 수 있습니다.");
			}
		}
		return sha256(key.getBytes(StandardCharsets.US_ASCII));
	}

	public static byte[] framed(String... values) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				for (String value : values) {
					byte[] encoded = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
					output.writeBoolean(value != null);
					output.writeInt(encoded.length);
					output.write(encoded);
				}
			}
			return sha256(bytes.toByteArray());
		} catch (IOException exception) {
			throw new IllegalStateException("In-memory digest framing failed", exception);
		}
	}

	private static byte[] sha256(byte[] value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required", exception);
		}
	}

	private static BusinessException invalid(String field, String reason) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, List.of(new FieldError(field, reason)));
	}
}
