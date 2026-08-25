package com.openmd.server.quiz.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openmd.server.quiz.domain.QuizGenerationCandidate.BlankCandidate;
import com.openmd.server.quiz.domain.QuizGenerationCandidate.ChoiceCandidate;
import com.openmd.server.quiz.domain.type.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizGenerationCandidateValidatorTest {

  private final QuizGenerationCandidateValidator validator = new QuizGenerationCandidateValidator();

  @Test
  void keepsOnlyValidCandidatesAndRenumbersThemInOriginalOrder() {
    var invalidChoiceCount =
        candidate(
            QuestionType.MULTIPLE_CHOICE,
            List.of(choice("A", true), choice("B", false)),
            List.of(),
            List.of(),
            null,
            List.of());
    var validShortAnswer =
        candidate(
            QuestionType.SHORT_ANSWER,
            List.of(),
            List.of(" FIFO ", "fifo"),
            List.of(),
            null,
            List.of());
    var invalidBlankMarker =
        new QuizGenerationCandidate(
            99,
            QuestionType.FILL_IN_THE_BLANK,
            "빈칸",
            "큐는 [2] 방식이다.",
            "해설",
            "근거",
            List.of(),
            List.of(),
            List.of(new BlankCandidate(1, List.of("FIFO"))),
            null,
            List.of());
    var validEssay =
        candidate(QuestionType.ESSAY, List.of(), List.of(), List.of(), "모범 답안", List.of("핵심"));

    List<ValidatedQuizQuestion> valid =
        validator.validateAll(
            List.of(invalidChoiceCount, validShortAnswer, invalidBlankMarker, validEssay));

    assertEquals(List.of(1, 2), valid.stream().map(ValidatedQuizQuestion::number).toList());
    assertEquals(
        List.of(QuestionType.SHORT_ANSWER, QuestionType.ESSAY),
        valid.stream().map(question -> question.candidate().type()).toList());
    assertEquals(List.of(" FIFO "), valid.getFirst().candidate().acceptedAnswers());
  }

  @Test
  void acceptsThreeFourAndFiveChoicesOnlyWhenExactlyOneIsCorrect() {
    for (int count = 3; count <= 5; count++) {
      List<ChoiceCandidate> choices =
          java.util.stream.IntStream.range(0, count)
              .mapToObj(index -> choice("보기 " + index, index == 0))
              .toList();
      assertEquals(
          1,
          validator
              .validateAll(
                  List.of(
                      candidate(
                          QuestionType.MULTIPLE_CHOICE,
                          choices,
                          List.of(),
                          List.of(),
                          null,
                          List.of())))
              .size());
    }
    assertEquals(
        0,
        validator
            .validateAll(
                List.of(
                    candidate(
                        QuestionType.MULTIPLE_CHOICE,
                        List.of(choice("A", true), choice("B", true), choice("C", false)),
                        List.of(),
                        List.of(),
                        null,
                        List.of())))
            .size());
  }

  @Test
  void rejectsOverflowingBlankMarkersWithoutThrowing() {
    QuizGenerationCandidate candidate =
        new QuizGenerationCandidate(
            1,
            QuestionType.FILL_IN_THE_BLANK,
            "빈칸",
            "값은 [999999999999999999999999999999]이다.",
            "해설",
            "근거",
            List.of(),
            List.of(),
            List.of(new BlankCandidate(1, List.of("값"))),
            null,
            List.of());

    List<ValidatedQuizQuestion> result =
        assertDoesNotThrow(() -> validator.validateAll(List.of(candidate)));

    assertEquals(0, result.size());
  }

  @Test
  void rejectsAnEssayWhenAnyKeyPointIsNullOrBlank() {
    QuizGenerationCandidate candidate =
        candidate(
            QuestionType.ESSAY,
            List.of(),
            List.of(),
            List.of(),
            "모범 답안",
            java.util.Arrays.asList("핵심", null, "  "));

    assertEquals(0, validator.validateAll(List.of(candidate)).size());
  }

  @Test
  void rejectsTopicsLongerThan255UnicodeCodePoints() {
    String topic = "😀".repeat(256);
    QuizGenerationCandidate candidate =
        new QuizGenerationCandidate(
            1,
            QuestionType.SHORT_ANSWER,
            topic,
            "문제",
            "해설",
            "근거",
            List.of(),
            List.of("답"),
            List.of(),
            null,
            List.of());

    assertEquals(0, validator.validateAll(List.of(candidate)).size());
  }

  private QuizGenerationCandidate candidate(
      QuestionType type,
      List<ChoiceCandidate> choices,
      List<String> acceptedAnswers,
      List<BlankCandidate> blanks,
      String modelAnswer,
      List<String> keyPoints) {
    return new QuizGenerationCandidate(
        42,
        type,
        "주제",
        type == QuestionType.FILL_IN_THE_BLANK ? "[1]" : "문제",
        "해설",
        "근거",
        choices,
        acceptedAnswers,
        blanks,
        modelAnswer,
        keyPoints);
  }

  private ChoiceCandidate choice(String text, boolean correct) {
    return new ChoiceCandidate(text, correct);
  }
}
