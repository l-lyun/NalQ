package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuizSetFailureCode;

public record QuizFailureView(QuizSetFailureCode code, String message, boolean retryable) {}
