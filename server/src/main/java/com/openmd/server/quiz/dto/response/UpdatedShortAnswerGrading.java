package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.GradingOutcome;

public record UpdatedShortAnswerGrading(
	String questionId,
	GradingOutcome outcome,
	long gradingRevision,
	ShortAnswerGradingSummary summary
) {
}
