package com.openmd.server.quiz.dto.request;

import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import com.openmd.server.quiz.dto.command.QuizGenerationConfig;
import java.util.List;

public record GenerateQuizRequest(
    List<QuestionType> selectedTypes, QuizDifficulty difficulty, Integer maxQuestionCount) {
  public QuizGenerationConfig toCommand() {
    return new QuizGenerationConfig(selectedTypes, difficulty, maxQuestionCount);
  }
}
