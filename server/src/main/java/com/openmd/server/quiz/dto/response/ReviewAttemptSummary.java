package com.openmd.server.quiz.dto.response;

public record ReviewAttemptSummary(
    GradingCount automaticGrading,
    EssaySelfAssessmentSummary essaySelfAssessment,
    int resolvedQuestionCount,
    int unresolvedQuestionCount) {}
