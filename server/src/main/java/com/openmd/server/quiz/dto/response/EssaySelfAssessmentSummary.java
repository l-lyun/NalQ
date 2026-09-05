package com.openmd.server.quiz.dto.response;

public record EssaySelfAssessmentSummary(int correctCount, int partialCount, int incorrectCount) {
}
