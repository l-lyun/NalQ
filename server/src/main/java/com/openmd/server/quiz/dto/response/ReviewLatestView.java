package com.openmd.server.quiz.dto.response;

public record ReviewLatestView(
    String sourceAttemptId,
    String quizSetId,
    Integer attemptNumber,
    int reviewQuestionCount,
    String activeReviewSessionId) {}
