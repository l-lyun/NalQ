package com.openmd.server.quiz.dto.response;

import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import java.util.List;

public record RequestedQuizConfig(
    List<QuestionType> selectedTypes, QuizDifficulty difficulty, Integer maxQuestionCount) {}
