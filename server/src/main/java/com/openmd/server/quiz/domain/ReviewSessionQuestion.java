package com.openmd.server.quiz.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_review_session_questions")
public class ReviewSessionQuestion extends BaseEntity {

	@Column(name = "review_session_id", nullable = false, updatable = false)
	private long reviewSessionId;
	@Column(name = "question_id", nullable = false, updatable = false)
	private long questionId;
	@Column(name = "sequence_number", nullable = false, updatable = false)
	private int sequenceNumber;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ReviewQuestionStatus status;

	protected ReviewSessionQuestion() {
	}

	public static ReviewSessionQuestion pending(long reviewSessionId, long questionId, int sequenceNumber) {
		ReviewSessionQuestion item = new ReviewSessionQuestion();
		item.reviewSessionId = reviewSessionId;
		item.questionId = questionId;
		item.sequenceNumber = sequenceNumber;
		item.status = ReviewQuestionStatus.PENDING;
		return item;
	}

	public long getReviewSessionId() { return reviewSessionId; }
	public long getQuestionId() { return questionId; }
	public int getSequenceNumber() { return sequenceNumber; }
	public ReviewQuestionStatus getStatus() { return status; }
}
