package com.openmd.server.quiz.service;

import com.openmd.server.quiz.domain.type.QuestionType;
import java.util.List;

record TemporaryQuizGenerationRequested(
    long userId, String quizSetId, List<QuestionType> selectedTypes, int maxQuestionCount) {

  TemporaryQuizGenerationRequested {
    selectedTypes = List.copyOf(selectedTypes);
  }
}
