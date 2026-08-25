package com.openmd.server.quiz.dto.response;

import java.util.List;

public record ReviewSessionView(
    String reviewSessionId,
    String sourceAttemptId,
    String status,
    int reviewQuestionCount,
    List<String> pendingEssayQuestionIds,
    List<QuizQuestionView> questions) {}
