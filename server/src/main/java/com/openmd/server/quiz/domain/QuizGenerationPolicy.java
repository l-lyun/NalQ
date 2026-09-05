package com.openmd.server.quiz.domain;

import com.openmd.server.quiz.domain.type.QuestionType;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuizGenerationPolicy {
  private static final Map<QuestionType, Integer> WEIGHTS =
      Map.of(
          QuestionType.MULTIPLE_CHOICE, 5,
          QuestionType.SHORT_ANSWER, 3,
          QuestionType.FILL_IN_THE_BLANK, 3,
          QuestionType.ESSAY, 2);

  public int minimumAcceptableTotal(int targetTotal) {
    return (int) Math.ceil(targetTotal * 0.8d);
  }

  public Map<QuestionType, Integer> targetByType(
      List<QuestionType> selectedTypes, int targetTotal) {
    int totalWeight = selectedTypes.stream().mapToInt(WEIGHTS::get).sum();
    Map<QuestionType, Integer> result = new LinkedHashMap<>();
    Map<QuestionType, Double> remainders = new EnumMap<>(QuestionType.class);
    int assigned = 0;
    for (QuestionType type : selectedTypes) {
      double exact = (double) targetTotal * WEIGHTS.get(type) / totalWeight;
      int base = (int) Math.floor(exact);
      result.put(type, base);
      remainders.put(type, exact - base);
      assigned += base;
    }
    Map<QuestionType, Integer> order = new HashMap<>();
    for (int index = 0; index < selectedTypes.size(); index++) {
      order.put(selectedTypes.get(index), index);
    }
    List<QuestionType> priority =
        selectedTypes.stream()
            .sorted(
                Comparator.<QuestionType, Double>comparing(remainders::get)
                    .reversed()
                    .thenComparingInt(order::get))
            .toList();
    for (int index = 0; assigned < targetTotal; index++, assigned++) {
      QuestionType type = priority.get(index % priority.size());
      result.put(type, result.get(type) + 1);
    }
    return Map.copyOf(result);
  }
}
