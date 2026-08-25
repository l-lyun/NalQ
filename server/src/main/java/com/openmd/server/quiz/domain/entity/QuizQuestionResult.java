package com.openmd.server.quiz.domain.entity;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_question_results")
public class QuizQuestionResult extends BaseEntity {

	@Column(name = "attempt_id", nullable = false, updatable = false)
	private long attemptId;

	@Column(name = "question_id", nullable = false, updatable = false)
	private long questionId;

	@Column(name = "submitted_answer", columnDefinition = "TEXT", updatable = false)
	private String submittedAnswer;

	@Enumerated(EnumType.STRING)
	@Column(name = "automatic_outcome", nullable = false, length = 16, updatable = false)
	private GradingOutcome automaticOutcome;

	@Enumerated(EnumType.STRING)
	@Column(name = "user_override_outcome", length = 16)
	private GradingOutcome userOverrideOutcome;

	@Column(name = "review_resolved", nullable = false)
	private boolean reviewResolved;

	protected QuizQuestionResult() {
	}

	public static QuizQuestionResult automatic(
		long attemptId,
		long questionId,
		String submittedAnswer,
		GradingOutcome automaticOutcome
	) {
		QuizQuestionResult result = new QuizQuestionResult();
		result.attemptId = attemptId;
		result.questionId = questionId;
		result.submittedAnswer = submittedAnswer;
		result.automaticOutcome = automaticOutcome;
		result.reviewResolved = false;
		return result;
	}

	public boolean overrideWith(GradingOutcome outcome) {
		if (currentOutcome() == outcome) {
			return false;
		}
		this.userOverrideOutcome = outcome;
		return true;
	}

	public GradingOutcome currentOutcome() {
		return userOverrideOutcome == null ? automaticOutcome : userOverrideOutcome;
	}

	public boolean isAnswered() {
		return submittedAnswer != null;
	}

	public long getAttemptId() { return attemptId; }
	public long getQuestionId() { return questionId; }
	public String getSubmittedAnswer() { return submittedAnswer; }
	public GradingOutcome getAutomaticOutcome() { return automaticOutcome; }
	public GradingOutcome getUserOverrideOutcome() { return userOverrideOutcome; }
	public boolean isReviewResolved() { return reviewResolved; }
}
