package com.openmd.server.quiz.service;

import com.openmd.server.quiz.domain.QuizGenerationCandidate;
import java.util.List;

public record QuizGeneratedBatch(Outcome outcome, List<QuizGenerationCandidate> candidates) {
  public QuizGeneratedBatch {
    candidates = candidates == null ? List.of() : List.copyOf(candidates);
  }

  public static QuizGeneratedBatch generated(List<QuizGenerationCandidate> candidates) {
    return new QuizGeneratedBatch(Outcome.GENERATED, candidates);
  }

  public static QuizGeneratedBatch sourceInsufficient() {
    return new QuizGeneratedBatch(Outcome.SOURCE_INSUFFICIENT, List.of());
  }

  public static QuizGeneratedBatch failed() {
    return new QuizGeneratedBatch(Outcome.FAILED, List.of());
  }

  public enum Outcome { GENERATED, SOURCE_INSUFFICIENT, FAILED }
}
