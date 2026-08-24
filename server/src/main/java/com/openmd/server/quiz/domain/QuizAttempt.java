package com.openmd.server.quiz.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempts")
public class QuizAttempt extends BaseEntity {

	@Column(name = "public_id", nullable = false, updatable = false, length = 36, unique = true)
	private String publicId;

	@Column(name = "quiz_set_id", nullable = false, updatable = false)
	private long quizSetId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private QuizAttemptStatus status;

	@Column(name = "automatic_correct_count", nullable = false)
	private int automaticCorrectCount;

	@Column(name = "automatic_graded_count", nullable = false)
	private int automaticGradedCount;

	@Column(name = "summary_revision", nullable = false)
	private long summaryRevision;

	protected QuizAttempt() {
	}

	public static QuizAttempt completed(long quizSetId, long userId, int correctCount, int gradedCount) {
		QuizAttempt attempt = new QuizAttempt();
		attempt.publicId = UUID.randomUUID().toString();
		attempt.quizSetId = quizSetId;
		attempt.userId = userId;
		attempt.status = QuizAttemptStatus.COMPLETED;
		attempt.automaticCorrectCount = correctCount;
		attempt.automaticGradedCount = gradedCount;
		attempt.summaryRevision = 0;
		return attempt;
	}

	public void incrementSummaryRevision() {
		summaryRevision++;
	}

	public String getPublicId() { return publicId; }
	public long getQuizSetId() { return quizSetId; }
	public long getUserId() { return userId; }
	public QuizAttemptStatus getStatus() { return status; }
	public int getAutomaticCorrectCount() { return automaticCorrectCount; }
	public int getAutomaticGradedCount() { return automaticGradedCount; }
	public long getSummaryRevision() { return summaryRevision; }
}
