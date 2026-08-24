package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.type.ReviewSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "quiz_review_sessions")
public class ReviewSession extends BaseEntity {

	@Column(name = "public_id", nullable = false, updatable = false, unique = true, length = 36)
	private String publicId;
	@Column(name = "user_id", nullable = false, updatable = false)
	private long userId;
	@Column(name = "source_attempt_id", nullable = false, updatable = false)
	private long sourceAttemptId;
	@Column(name = "source_summary_revision", nullable = false, updatable = false)
	private long sourceSummaryRevision;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ReviewSessionStatus status;

	protected ReviewSession() {
	}

	public static ReviewSession active(long userId, long sourceAttemptId, long sourceSummaryRevision) {
		ReviewSession session = new ReviewSession();
		session.publicId = UUID.randomUUID().toString();
		session.userId = userId;
		session.sourceAttemptId = sourceAttemptId;
		session.sourceSummaryRevision = sourceSummaryRevision;
		session.status = ReviewSessionStatus.ACTIVE;
		return session;
	}

	public String getPublicId() { return publicId; }
	public long getUserId() { return userId; }
	public long getSourceAttemptId() { return sourceAttemptId; }
	public long getSourceSummaryRevision() { return sourceSummaryRevision; }
	public ReviewSessionStatus getStatus() { return status; }
}
