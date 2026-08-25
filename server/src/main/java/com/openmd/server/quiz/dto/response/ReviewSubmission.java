package com.openmd.server.quiz.dto.response;

import java.time.Instant;
import java.util.List;

public record ReviewSubmission(
    String reviewSessionId,
    String status,
    GradingCount automaticGrading,
    List<String> pendingEssayQuestionIds,
    Instant submittedAt) {}
