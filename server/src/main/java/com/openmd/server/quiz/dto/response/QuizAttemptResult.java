package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import java.util.List;

public record QuizAttemptResult(
    String attemptId,
    String quizSetId,
    QuizAttemptStatus status,
    boolean reviewAvailable,
    QuizAttemptSummary summary,
    List<QuizQuestionResultView> questionResults) {}
