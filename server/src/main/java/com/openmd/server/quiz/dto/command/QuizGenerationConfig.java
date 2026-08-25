package com.openmd.server.quiz.dto.command;

import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import java.util.List;

public record QuizGenerationConfig(
    List<QuestionType> selectedTypes, QuizDifficulty difficulty, Integer maxQuestionCount) {}
