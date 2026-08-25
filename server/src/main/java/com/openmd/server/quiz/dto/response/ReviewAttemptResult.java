package com.openmd.server.quiz.dto.response;

import java.util.List;

public record ReviewAttemptResult(
    String reviewSessionId,
    String sourceAttemptId,
    String status,
    ReviewAttemptSummary summary,
    List<QuizQuestionResultView> questionResults) {}
