package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import java.time.Instant;
import java.util.List;

public record SubmittedQuizAttempt(
	String attemptId,
	QuizAttemptStatus status,
	GradingCount automaticGrading,
	List<String> pendingEssayQuestionIds,
	Instant createdAt
) {
}
