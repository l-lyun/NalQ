package com.openmd.server.quiz.dto.response;

import java.time.Instant;

public record RenamedQuizSet(String quizSetId, String quizTitle, Instant updatedAt) {}
