package com.openmd.server.quiz.domain;

import com.openmd.server.quiz.domain.type.QuestionType;
import java.util.List;

public record QuizGenerationCandidate(
    Integer proposedNumber,
    QuestionType type,
    String topic,
    String prompt,
    String explanation,
    String sourceExcerpt,
    List<ChoiceCandidate> choices,
    List<String> acceptedAnswers,
    List<BlankCandidate> blanks,
    String modelAnswer,
    List<String> keyPoints) {
  public record ChoiceCandidate(String text, boolean correct) {}

  public record BlankCandidate(int number, List<String> acceptedAnswers) {}
}
