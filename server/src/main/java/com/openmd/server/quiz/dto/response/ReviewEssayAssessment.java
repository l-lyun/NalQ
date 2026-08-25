package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.GradingOutcome;

public record ReviewEssayAssessment(
    String questionId,
    GradingOutcome assessment,
    String reviewStatus,
    String status,
    int remainingSelfAssessmentCount) {}
