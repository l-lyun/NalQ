package com.openmd.server.quiz.dto.response;

public record QuizAttemptSummary(
	GradingCount scoredGrading,
	EssaySelfAssessmentSummary essaySelfAssessment,
	int reviewQuestionCount
) {
}
