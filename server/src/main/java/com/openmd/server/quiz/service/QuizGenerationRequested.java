package com.openmd.server.quiz.service;

import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import java.util.List;

record QuizGenerationRequested(
    long userId,
    String quizSetId,
    List<QuestionType> selectedTypes,
    QuizDifficulty difficulty,
    int maxQuestionCount,
    String generationPrompt,
    String learningMaterial) {

  QuizGenerationRequested {
    selectedTypes = List.copyOf(selectedTypes);
    generationPrompt = generationPrompt == null ? "" : generationPrompt;
  }
}
