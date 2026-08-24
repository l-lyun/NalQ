package com.openmd.server.quiz.dto.model;

import com.openmd.server.quiz.dto.response.SubmittedQuizAttempt;

public record QuizAttemptSubmissionResult(boolean created, SubmittedQuizAttempt attempt) {
}
