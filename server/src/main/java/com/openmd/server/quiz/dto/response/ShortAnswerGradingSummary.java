package com.openmd.server.quiz.dto.response;

public record ShortAnswerGradingSummary(
	long revision,
	GradingCount scoredGrading,
	int reviewQuestionCount
) {
}
