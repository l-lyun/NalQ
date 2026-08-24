package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.GradingOutcome;
import com.openmd.server.quiz.domain.QuestionType;

public record ShortAnswerQuestionResult(
	String questionId,
	int number,
	QuestionType type,
	String topic,
	String prompt,
	AnswerValue response,
	AnswerValue representativeAnswer,
	GradingOutcome outcome,
	long gradingRevision,
	String explanation,
	String sourceExcerpt
) {
}
