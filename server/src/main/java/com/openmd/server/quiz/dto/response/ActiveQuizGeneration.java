package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuizSetStatus;

public record ActiveQuizGeneration(
    String quizSetId,
    String materialId,
    String quizTitle,
    QuizSetStatus status,
    int pollAfterSeconds) {}
