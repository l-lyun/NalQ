package com.openmd.server.quiz.dto.response;

public record QuizAttemptSummary(
	long revision,
	GradingCount scoredGrading,
	EssaySelfAssessmentSummary essaySelfAssessment,
	int reviewQuestionCount
) {
}
