package com.openmd.server.quiz.domain;

import com.openmd.server.quiz.domain.QuizGenerationCandidate.BlankCandidate;
import com.openmd.server.quiz.domain.QuizGenerationCandidate.ChoiceCandidate;
import com.openmd.server.quiz.domain.type.QuestionType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QuizGenerationCandidateValidator {
  private static final Pattern BLANK_MARKER = Pattern.compile("\\[(\\d+)]");
  private static final Pattern WHITESPACE = Pattern.compile("[\\p{javaWhitespace}\\p{Z}]+");

  public List<ValidatedQuizQuestion> validateAll(List<QuizGenerationCandidate> candidates) {
    return validateAll(candidates, List.of(QuestionType.values()), null);
  }

  public List<ValidatedQuizQuestion> validateAll(
      List<QuizGenerationCandidate> candidates,
      List<QuestionType> selectedTypes,
      String learningMaterial) {
    if (candidates == null || selectedTypes == null) return List.of();
    Set<QuestionType> allowed = Set.copyOf(selectedTypes);
    Set<String> prompts = new HashSet<>();
    List<ValidatedQuizQuestion> valid = new ArrayList<>();
    for (QuizGenerationCandidate original : candidates) {
      QuizGenerationCandidate candidate = validated(original, allowed, learningMaterial);
      if (candidate == null || !prompts.add(normalizedPrompt(candidate.prompt()))) continue;
      valid.add(new ValidatedQuizQuestion(valid.size() + 1, candidate));
    }
    return List.copyOf(valid);
  }

  private QuizGenerationCandidate validated(
      QuizGenerationCandidate value, Set<QuestionType> allowed, String learningMaterial) {
    if (value == null
        || value.type() == null
        || !allowed.contains(value.type())
        || invalid(value.topic(), 100)
        || invalid(value.prompt(), 1_000)
        || invalid(value.explanation(), 1_000)
        || invalid(value.sourceExcerpt(), 500)
        || !sourceContains(learningMaterial, value.sourceExcerpt())) return null;
    return switch (value.type()) {
      case MULTIPLE_CHOICE -> validChoices(value) ? value : null;
      case SHORT_ANSWER -> validShortAnswer(value);
      case FILL_IN_THE_BLANK -> validBlanks(value) ? withBlankAnswers(value) : null;
      case ESSAY -> validEssay(value) ? value : null;
    };
  }

  private boolean validChoices(QuizGenerationCandidate value) {
    List<ChoiceCandidate> choices = value.choices();
    return choices != null
        && choices.size() >= 3
        && choices.size() <= 5
        && choices.stream().allMatch(choice -> choice != null && !invalid(choice.text(), 300))
        && choices.stream()
                .map(choice -> normalize(normalizeWhitespace(choice.text())))
                .distinct()
                .count()
            == choices.size()
        && choices.stream().filter(ChoiceCandidate::correct).count() == 1
        && empty(value.acceptedAnswers())
        && empty(value.blanks())
        && empty(value.modelAnswer())
        && empty(value.keyPoints());
  }

  private QuizGenerationCandidate validShortAnswer(QuizGenerationCandidate value) {
    if (!empty(value.choices())
        || !empty(value.blanks())
        || !empty(value.modelAnswer())
        || !empty(value.keyPoints())) return null;
    List<String> answers = uniqueAnswers(value.acceptedAnswers());
    return answers == null || answers.isEmpty() || answers.size() > 5
        ? null
        : copy(value, answers, value.blanks());
  }

  private QuizGenerationCandidate withBlankAnswers(QuizGenerationCandidate value) {
    if (!empty(value.choices())
        || !empty(value.acceptedAnswers())
        || !empty(value.modelAnswer())
        || !empty(value.keyPoints())) return null;
    List<BlankCandidate> blanks = new ArrayList<>();
    for (BlankCandidate blank : value.blanks()) {
      List<String> answers = uniqueAnswers(blank.acceptedAnswers());
      if (answers == null || answers.isEmpty() || answers.size() > 5) return null;
      blanks.add(new BlankCandidate(blank.number(), answers));
    }
    return copy(value, value.acceptedAnswers(), List.copyOf(blanks));
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

  private boolean validEssay(QuizGenerationCandidate value) {
    return empty(value.choices())
        && empty(value.acceptedAnswers())
        && empty(value.blanks())
        && !invalid(value.modelAnswer(), 1_500)
        && value.keyPoints() != null
        && !value.keyPoints().isEmpty()
        && value.keyPoints().size() <= 5
        && value.keyPoints().stream().allMatch(point -> !invalid(point, 300));
  }

  private boolean sourceContains(String learningMaterial, String excerpt) {
    return learningMaterial == null
        || normalizeWhitespace(learningMaterial).contains(normalizeWhitespace(excerpt));
  }

  private List<String> uniqueAnswers(List<String> answers) {
    if (answers == null) return null;
    LinkedHashMap<String, String> unique = new LinkedHashMap<>();
    for (String answer : answers) {
      if (invalid(answer, 200)) return null;
      unique.putIfAbsent(ShortAnswerGrader.normalize(answer), answer);
    }
    return List.copyOf(unique.values());
  }

  private boolean invalid(String value, int maxCodePoints) {
    return blank(value) || value.codePointCount(0, value.length()) > maxCodePoints;
  }

  private boolean empty(List<?> values) {
    return values != null && values.isEmpty();
  }

  private boolean empty(String value) {
    return value != null && value.isEmpty();
  }

  private boolean blank(String value) {
    return value == null
        || value.codePoints().allMatch(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp));
  }

  private String normalizeWhitespace(String value) {
    return WHITESPACE.matcher(value.strip()).replaceAll(" ");
  }

  private String normalizedPrompt(String value) {
    return normalize(normalizeWhitespace(value));
  }

  private String normalize(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }

  private QuizGenerationCandidate copy(
      QuizGenerationCandidate source, List<String> acceptedAnswers, List<BlankCandidate> blanks) {
    return new QuizGenerationCandidate(
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
