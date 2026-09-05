package com.openmd.server.quiz.service;

import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import java.util.List;
import java.util.Map;

public record QuizGenerationWork(
    Task task,
    QuizSpec quizSpec,
    String generationRequest,
    String learningMaterial,
    List<ExcludedQuestion> excludedQuestions) {

  public QuizGenerationWork {
    excludedQuestions = List.copyOf(excludedQuestions);
  }

  public enum Task { INITIAL, SUPPLEMENT }

  public record QuizSpec(
      QuizDifficulty difficulty,
      int targetTotal,
      int minimumAcceptableTotal,
      Map<QuestionType, Integer> targetByType) {
    public QuizSpec {
      targetByType = Map.copyOf(targetByType);
    }
  }

  public record ExcludedQuestion(
      QuestionType type, String topic, String prompt, String sourceExcerpt) {}
}
