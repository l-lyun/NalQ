package com.openmd.server.quiz.dto.response;

import java.time.Instant;

public record ReviewLatestView(
    String sourceAttemptId,
    String quizSetId,
    Integer attemptNumber,
    String quizTitle,
    String materialTitle,
    Instant completedAt,
    int totalQuestionCount,
    int reviewQuestionCount,
    String activeReviewSessionId) {}
