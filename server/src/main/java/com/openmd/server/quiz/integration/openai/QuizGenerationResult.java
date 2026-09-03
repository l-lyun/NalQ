package com.openmd.server.quiz.integration.openai;

import com.openmd.server.quiz.domain.QuizGenerationCandidate;
import java.util.List;

public record QuizGenerationResult(
    GenerationOutcome outcome,
    InsufficiencyReason insufficiencyReason,
    List<QuizGenerationCandidate> questions) {

  public enum GenerationOutcome { GENERATED, SOURCE_INSUFFICIENT }

  public enum InsufficiencyReason {
    NONE,
    TOO_LITTLE_CONTENT,
    NO_ASSESSABLE_FACTS,
    INSUFFICIENT_DISTINCT_FACTS
  }
}
