package com.openmd.server.quiz.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_short_answer_grading_idempotencies")
public class ShortAnswerGradingIdempotency extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false)
	private long userId;
	@Column(name = "attempt_id", nullable = false, updatable = false)
	private long attemptId;
	@Column(name = "question_id", nullable = false, updatable = false)
	private long questionId;
	@Column(name = "idempotency_key_hash", nullable = false, updatable = false, columnDefinition = "BINARY(32)")
	private byte[] idempotencyKeyHash;
	@Column(name = "request_fingerprint", nullable = false, updatable = false, columnDefinition = "BINARY(32)")
	private byte[] requestFingerprint;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private GradingOutcome outcome;
	@Column(name = "grading_revision", nullable = false)
	private long gradingRevision;
	@Column(name = "summary_revision", nullable = false)
	private long summaryRevision;
	@Column(name = "correct_question_count", nullable = false)
	private int correctQuestionCount;
	@Column(name = "graded_question_count", nullable = false)
	private int gradedQuestionCount;
	@Column(name = "review_question_count", nullable = false)
	private int reviewQuestionCount;

	protected ShortAnswerGradingIdempotency() {
	}

	public static ShortAnswerGradingIdempotency of(
		long userId, long attemptId, long questionId, byte[] keyHash, byte[] fingerprint,
		GradingOutcome outcome, long gradingRevision, long summaryRevision,
		int correctQuestionCount, int gradedQuestionCount, int reviewQuestionCount
	) {
		ShortAnswerGradingIdempotency value = new ShortAnswerGradingIdempotency();
		value.userId = userId;
		value.attemptId = attemptId;
		value.questionId = questionId;
		value.idempotencyKeyHash = keyHash.clone();
		value.requestFingerprint = fingerprint.clone();
		value.outcome = outcome;
		value.gradingRevision = gradingRevision;
		value.summaryRevision = summaryRevision;
		value.correctQuestionCount = correctQuestionCount;
		value.gradedQuestionCount = gradedQuestionCount;
		value.reviewQuestionCount = reviewQuestionCount;
		return value;
	}

	public byte[] getRequestFingerprint() { return requestFingerprint.clone(); }
	public GradingOutcome getOutcome() { return outcome; }
	public long getGradingRevision() { return gradingRevision; }
	public long getSummaryRevision() { return summaryRevision; }
	public int getCorrectQuestionCount() { return correctQuestionCount; }
	public int getGradedQuestionCount() { return gradedQuestionCount; }
	public int getReviewQuestionCount() { return reviewQuestionCount; }
}
