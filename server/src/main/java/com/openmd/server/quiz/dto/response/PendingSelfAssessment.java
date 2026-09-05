package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import java.util.List;

public record PendingSelfAssessment(
    String attemptId,
    String quizSetId,
    QuizAttemptStatus status,
    List<String> pendingEssayQuestionIds) {}
