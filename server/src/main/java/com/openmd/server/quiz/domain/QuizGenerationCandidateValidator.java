package com.openmd.server.quiz.domain;

import com.openmd.server.quiz.domain.QuizGenerationCandidate.BlankCandidate;
import com.openmd.server.quiz.domain.QuizGenerationCandidate.ChoiceCandidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuizGenerationCandidateValidator {
  private static final Pattern BLANK_MARKER = Pattern.compile("\\[(\\d+)]");

  public List<ValidatedQuizQuestion> validateAll(List<QuizGenerationCandidate> candidates) {
    if (candidates == null) return List.of();
    List<ValidatedQuizQuestion> valid = new ArrayList<>();
    for (QuizGenerationCandidate original : candidates) {
      QuizGenerationCandidate candidate = validated(original);
      if (candidate != null) valid.add(new ValidatedQuizQuestion(valid.size() + 1, candidate));
    }
    return List.copyOf(valid);
  }

  private QuizGenerationCandidate validated(QuizGenerationCandidate value) {
    if (value == null
        || value.type() == null
        || blank(value.topic())
        || value.topic().codePointCount(0, value.topic().length()) > 255
        || blank(value.prompt())
        || blank(value.explanation())
        || blank(value.sourceExcerpt())) return null;
    return switch (value.type()) {
      case MULTIPLE_CHOICE -> validChoices(value) ? value : null;
      case SHORT_ANSWER -> withAcceptedAnswers(value);
      case FILL_IN_THE_BLANK -> validBlanks(value) ? withBlankAnswers(value) : null;
      case ESSAY -> !blank(value.modelAnswer()) && validKeyPoints(value.keyPoints()) ? value : null;
    };
  }

  private boolean validChoices(QuizGenerationCandidate value) {
    List<ChoiceCandidate> choices = value.choices();
    return choices != null
        && choices.size() >= 3
        && choices.size() <= 5
        && choices.stream().allMatch(choice -> choice != null && !blank(choice.text()))
        && choices.stream().filter(ChoiceCandidate::correct).count() == 1;
  }

  private QuizGenerationCandidate withAcceptedAnswers(QuizGenerationCandidate value) {
    List<String> answers = uniqueAnswers(value.acceptedAnswers());
    return answers.isEmpty() ? null : copy(value, answers, value.blanks());
  }

  private QuizGenerationCandidate withBlankAnswers(QuizGenerationCandidate value) {
    List<BlankCandidate> blanks =
        value.blanks().stream()
            .map(
                blank -> new BlankCandidate(blank.number(), uniqueAnswers(blank.acceptedAnswers())))
            .toList();
    return blanks.stream().anyMatch(blank -> blank.acceptedAnswers().isEmpty())
        ? null
        : copy(value, value.acceptedAnswers(), blanks);
  }

  private boolean validBlanks(QuizGenerationCandidate value) {
    List<BlankCandidate> blanks = value.blanks();
    if (blanks == null || blanks.size() < 1 || blanks.size() > 2) return false;
    for (int index = 0; index < blanks.size(); index++) {
      if (blanks.get(index) == null || blanks.get(index).number() != index + 1) return false;
    }
    List<Integer> markers = new ArrayList<>();
    Matcher matcher = BLANK_MARKER.matcher(value.prompt());
    while (matcher.find()) {
      try {
        markers.add(Integer.parseInt(matcher.group(1)));
      } catch (NumberFormatException exception) {
        return false;
      }
    }
    return markers.equals(
        java.util.stream.IntStream.rangeClosed(1, blanks.size()).boxed().toList());
  }

  private List<String> uniqueAnswers(List<String> answers) {
    if (answers == null) return List.of();
    LinkedHashMap<String, String> unique = new LinkedHashMap<>();
    for (String answer : answers) {
      String normalized = answer == null ? "" : ShortAnswerGrader.normalize(answer);
      if (!normalized.isEmpty()) unique.putIfAbsent(normalized, answer);
    }
    return List.copyOf(unique.values());
  }

  private boolean validKeyPoints(List<String> values) {
    return values != null && !values.isEmpty() && values.stream().allMatch(value -> !blank(value));
  }

  private boolean blank(String value) {
    return value == null
        || value
            .codePoints()
            .allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
  }

  private QuizGenerationCandidate copy(
      QuizGenerationCandidate source, List<String> acceptedAnswers, List<BlankCandidate> blanks) {
    return new QuizGenerationCandidate(
        source.proposedNumber(),
        source.type(),
        source.topic(),
        source.prompt(),
        source.explanation(),
        source.sourceExcerpt(),
        source.choices(),
        acceptedAnswers,
        blanks,
        source.modelAnswer(),
        source.keyPoints());
  }
}
