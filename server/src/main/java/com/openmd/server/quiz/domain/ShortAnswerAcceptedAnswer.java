package com.openmd.server.quiz.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_short_answer_accepted_answers")
public class ShortAnswerAcceptedAnswer extends BaseEntity {

	@Column(nullable = false, columnDefinition = "TEXT")
	private String answer;

	@Column(name = "normalized_answer", nullable = false, length = 1000)
	private String normalizedAnswer;

	protected ShortAnswerAcceptedAnswer() {
	}

	static ShortAnswerAcceptedAnswer of(String answer, String normalizedAnswer) {
		ShortAnswerAcceptedAnswer value = new ShortAnswerAcceptedAnswer();
		value.answer = answer;
		value.normalizedAnswer = normalizedAnswer;
		return value;
	}

	public String getAnswer() { return answer; }
	public String getNormalizedAnswer() { return normalizedAnswer; }
}
