package com.openmd.server.learningmaterial.dto.model;

import com.openmd.server.learningmaterial.domain.SourceType;
import java.time.Instant;

public record StoredLearningMaterial(
	long id,
	long userId,
	String title,
	String content,
	SourceType sourceType,
	byte[] requestFingerprint,
	Instant createdAt
) {
	public StoredLearningMaterial {
		requestFingerprint = requestFingerprint.clone();
	}

	@Override
	public byte[] requestFingerprint() {
		return requestFingerprint.clone();
	}
}
