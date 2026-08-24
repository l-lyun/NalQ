package com.openmd.server.learningmaterial.dto.model;

import com.openmd.server.learningmaterial.domain.SourceType;

public record NewLearningMaterial(
	long userId,
	String title,
	String content,
	SourceType sourceType,
	byte[] idempotencyKeyHash,
	byte[] requestFingerprint
) {
	public NewLearningMaterial {
		idempotencyKeyHash = idempotencyKeyHash.clone();
		requestFingerprint = requestFingerprint.clone();
	}

	@Override
	public byte[] idempotencyKeyHash() {
		return idempotencyKeyHash.clone();
	}

	@Override
	public byte[] requestFingerprint() {
		return requestFingerprint.clone();
	}
}
