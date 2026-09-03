package com.openmd.server.quiz.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openmd.server.quiz.domain.type.QuestionType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuizGenerationPolicyTest {

  private final QuizGenerationPolicy policy = new QuizGenerationPolicy();

  @Test
  void allocatesPreferredCountsByWeightAndUsesSelectionOrderForTies() {
    assertEquals(
        Map.of(
            QuestionType.MULTIPLE_CHOICE, 5,
            QuestionType.SHORT_ANSWER, 3,
            QuestionType.ESSAY, 2),
        policy.targetByType(
            List.of(
                QuestionType.MULTIPLE_CHOICE,
                QuestionType.SHORT_ANSWER,
                QuestionType.ESSAY),
            10));
    assertEquals(
        Map.of(QuestionType.FILL_IN_THE_BLANK, 3, QuestionType.SHORT_ANSWER, 2),
        policy.targetByType(
            List.of(QuestionType.FILL_IN_THE_BLANK, QuestionType.SHORT_ANSWER), 5));
  }

  @Test
  void usesTheDocumentedEightyPercentThresholds() {
    assertEquals(4, policy.minimumAcceptableTotal(5));
    assertEquals(8, policy.minimumAcceptableTotal(10));
    assertEquals(12, policy.minimumAcceptableTotal(15));
    assertEquals(16, policy.minimumAcceptableTotal(20));
  }
}
