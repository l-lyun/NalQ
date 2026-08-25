package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuizSetStatus;
import java.time.Instant;

public record AcceptedQuizGeneration(
    String quizSetId,
    String materialId,
    QuizSetStatus status,
    int pollAfterSeconds,
    RequestedQuizConfig requestedConfig,
    Instant createdAt) {}
