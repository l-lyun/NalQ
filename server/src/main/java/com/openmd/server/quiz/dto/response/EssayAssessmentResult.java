package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.*;

public record EssayAssessmentResult(
    String attemptId,
    String questionId,
    GradingOutcome assessment,
    QuizAttemptStatus status,
    int remainingSelfAssessmentCount) {}
