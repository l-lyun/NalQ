package com.openmd.server.quiz.dto.command;

public record QuizGenerationCommand(String contentRevision, QuizGenerationConfig config) {}
