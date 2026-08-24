package com.openmd.server.quiz.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_attempt_submissions")
public class QuizAttemptSubmission extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private long userId;

	@Column(name = "quiz_set_id", nullable = false, updatable = false)
	private long quizSetId;

	@Column(name = "idempotency_key_hash", nullable = false, updatable = false, columnDefinition = "BINARY(32)")
	private byte[] idempotencyKeyHash;

	@Column(name = "request_fingerprint", nullable = false, updatable = false, columnDefinition = "BINARY(32)")
	private byte[] requestFingerprint;

	@Column(name = "attempt_id", nullable = false, updatable = false)
	private long attemptId;

	protected QuizAttemptSubmission() {
	}

	public static QuizAttemptSubmission of(long userId, long quizSetId, byte[] keyHash, byte[] fingerprint, long attemptId) {
		QuizAttemptSubmission submission = new QuizAttemptSubmission();
		submission.userId = userId;
		submission.quizSetId = quizSetId;
		submission.idempotencyKeyHash = keyHash.clone();
		submission.requestFingerprint = fingerprint.clone();
		submission.attemptId = attemptId;
		return submission;
	}

	public byte[] getRequestFingerprint() { return requestFingerprint.clone(); }
	public long getAttemptId() { return attemptId; }
}
