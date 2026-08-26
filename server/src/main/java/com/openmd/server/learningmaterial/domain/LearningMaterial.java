package com.openmd.server.learningmaterial.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "learning_materials")
public class LearningMaterial extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private long userId;

	@Column(nullable = false, length = 255)
	private String title;

	@Column(nullable = false, columnDefinition = "MEDIUMTEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false, length = 16)
	private SourceType sourceType;

	@Column(name = "idempotency_key_hash", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] idempotencyKeyHash;

	@Column(name = "request_fingerprint", nullable = false, columnDefinition = "BINARY(32)")
	private byte[] requestFingerprint;

	protected LearningMaterial() {
	}

	private LearningMaterial(
		long userId,
		String title,
		String content,
		SourceType sourceType,
		byte[] idempotencyKeyHash,
		byte[] requestFingerprint
	) {
		this.userId = userId;
		this.title = title;
		this.content = content;
		this.sourceType = sourceType;
		this.idempotencyKeyHash = idempotencyKeyHash.clone();
		this.requestFingerprint = requestFingerprint.clone();
	}

	public static LearningMaterial create(
		long userId,
		String title,
		String content,
		SourceType sourceType,
		byte[] idempotencyKeyHash,
		byte[] requestFingerprint
	) {
		return new LearningMaterial(userId, title, content, sourceType, idempotencyKeyHash, requestFingerprint);
	}

	public long getUserId() { return userId; }
	public String getTitle() { return title; }
	public String getContent() { return content; }
	public SourceType getSourceType() { return sourceType; }
	public byte[] getIdempotencyKeyHash() { return idempotencyKeyHash.clone(); }
	public byte[] getRequestFingerprint() { return requestFingerprint.clone(); }

	public void updateTitle(String title) {
		this.title = title;
	}

	public void updateContent(String content) {
		this.content = content;
	}
}
