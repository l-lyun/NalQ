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
            "",
            List.of());
    var validShortAnswer =
        candidate(
            QuestionType.SHORT_ANSWER,
            List.of(),
            List.of(" FIFO ", "fifo"),
            List.of(),
            "",
            List.of());
    var invalidBlankMarker =
        new QuizGenerationCandidate(
            QuestionType.FILL_IN_THE_BLANK,
            "빈칸",
            "큐는 [2] 방식이다.",
            "해설",
            "근거",
            List.of(),
            List.of(),
            List.of(new BlankCandidate(1, List.of("FIFO"))),
            "",
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
                          "",
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
                        "",
                        List.of())))
            .size());
  }

  @Test
  void rejectsOverflowingBlankMarkersWithoutThrowing() {
    QuizGenerationCandidate candidate =
        new QuizGenerationCandidate(
            QuestionType.FILL_IN_THE_BLANK,
            "빈칸",
            "값은 [999999999999999999999999999999]이다.",
            "해설",
            "근거",
            List.of(),
            List.of(),
            List.of(new BlankCandidate(1, List.of("값"))),
            "",
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
  void rejectsTheWholeCandidateWhenOneAcceptedAnswerExceedsItsLimit() {
    QuizGenerationCandidate candidate =
        candidate(
            QuestionType.SHORT_ANSWER,
            List.of(),
            List.of("정답", "가".repeat(201)),
            List.of(),
            "",
            List.of());

    assertEquals(0, validator.validateAll(List.of(candidate)).size());
  }

  @Test
  void rejectsTopicsLongerThan255UnicodeCodePoints() {
    String topic = "😀".repeat(256);
    QuizGenerationCandidate candidate =
        new QuizGenerationCandidate(
            QuestionType.SHORT_ANSWER,
            topic,
            "문제",
            "해설",
            "근거",
            List.of(),
            List.of("답"),
            List.of(),
            "",
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
        type,
        "주제",
        type == QuestionType.FILL_IN_THE_BLANK ? "[1]" : "문제 " + type,
        "해설",
        "근거",
        choices,
        acceptedAnswers,
        blanks,
        modelAnswer,
        keyPoints);
  }

  @Test
  void rejectsInactiveFieldsSourceMismatchesDuplicatesAndLengthOverflow() {
    QuizGenerationCandidate invalidInactiveField =
        new QuizGenerationCandidate(
            QuestionType.SHORT_ANSWER,
            "주제",
            "정답은?",
            "해설",
            "실제 근거",
            List.of(choice("사용하면 안 됨", true), choice("B", false), choice("C", false)),
            List.of("답"),
            List.of(),
            "",
            List.of());
    QuizGenerationCandidate valid =
        new QuizGenerationCandidate(
            QuestionType.SHORT_ANSWER,
            "동시성",
            "뮤텍스의 목적은?",
            "상호 배제를 제공한다.",
            "뮤텍스는 상호 배제를 제공한다.",
            List.of(),
            List.of("상호 배제"),
            List.of(),
            "",
            List.of());
    QuizGenerationCandidate duplicate =
        new QuizGenerationCandidate(
            QuestionType.SHORT_ANSWER,
            "다른 주제",
            "뮤텍스의 목적은!!!",
            "해설",
            "뮤텍스는 상호 배제를 제공한다.",
            List.of(),
            List.of("상호 배제"),
            List.of(),
            "",
            List.of());

    List<ValidatedQuizQuestion> result =
        validator.validateAll(
            List.of(invalidInactiveField, valid, duplicate),
            List.of(QuestionType.SHORT_ANSWER),
            "뮤텍스는   상호 배제를 제공한다.");

    assertEquals(1, result.size());
    assertEquals("동시성", result.getFirst().candidate().topic());
  }

  @Test
  void treatsUnicodeSpaceSeparatorsAsEquivalentInSourceEvidence() {
    QuizGenerationCandidate candidate =
        new QuizGenerationCandidate(
            QuestionType.SHORT_ANSWER,
            "공백",
            "어떤 문자열인가요?",
            "해설",
            "알파 베타 감마",
            List.of(),
            List.of("문자열"),
            List.of(),
            "",
            List.of());

    assertEquals(
        1,
        validator
            .validateAll(
                List.of(candidate),
                List.of(QuestionType.SHORT_ANSWER),
                "알파\u00a0베타\u2003감마")
            .size());
  }

  private ChoiceCandidate choice(String text, boolean correct) {
    return new ChoiceCandidate(text, correct);
  }
}
