package com.openmd.server.quiz.service;

import com.openmd.server.global.error.*;
import com.openmd.server.quiz.domain.entity.*;
import com.openmd.server.quiz.domain.type.*;
import com.openmd.server.quiz.dto.response.*;
import com.openmd.server.quiz.repository.*;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptResultProjector {
  private final QuizSetRepository sets;
  private final QuizQuestionRepository questions;
  private final QuizAttemptQuestionRepository attemptQuestions;
  private final QuizSubmittedAnswerRepository submitted;
  private final QuizQuestionChoiceRepository choices;
  private final QuizShortAnswerAnswerRepository shorts;
  private final QuizFillInTheBlankRepository blanks;
  private final QuizFillInTheBlankAnswerRepository blankAnswers;
  private final QuizEssayAnswerGuideRepository essays;

  public QuizAttemptResultProjector(
      QuizSetRepository sets,
      QuizQuestionRepository questions,
      QuizAttemptQuestionRepository attemptQuestions,
      QuizSubmittedAnswerRepository submitted,
      QuizQuestionChoiceRepository choices,
      QuizShortAnswerAnswerRepository shorts,
      QuizFillInTheBlankRepository blanks,
      QuizFillInTheBlankAnswerRepository blankAnswers,
      QuizEssayAnswerGuideRepository essays) {
    this.sets = sets;
    this.questions = questions;
    this.attemptQuestions = attemptQuestions;
    this.submitted = submitted;
    this.choices = choices;
    this.shorts = shorts;
    this.blanks = blanks;
    this.blankAnswers = blankAnswers;
    this.essays = essays;
  }

  public QuizAttemptResult project(QuizAttempt attempt) {
    QuizSet set =
        sets.findById(attempt.getQuizSetId())
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    List<QuizAttemptQuestion> rows =
        attemptQuestions.findAllByAttemptIdOrderBySequenceNumber(attempt.getId());
    List<QuizQuestionResultView> projected = rows.stream().map(this::question).toList();
    int graded = 0, correct = 0, essayCorrect = 0, partial = 0, essayIncorrect = 0, review = 0;
    for (int i = 0; i < rows.size(); i++) {
      QuizQuestion q = questions.findById(rows.get(i).getQuestionId()).orElseThrow();
      GradingOutcome outcome = rows.get(i).getFinalGradingResult();
      if (q.getType() == QuestionType.ESSAY) {
        if (outcome == GradingOutcome.CORRECT) essayCorrect++;
        else if (outcome == GradingOutcome.PARTIAL) partial++;
        else if (outcome == GradingOutcome.INCORRECT) essayIncorrect++;
      } else {
        graded++;
        if (outcome == GradingOutcome.CORRECT) correct++;
      }
      if ((outcome == GradingOutcome.INCORRECT || outcome == GradingOutcome.PARTIAL)
          && rows.get(i).getReviewResolvedAt() == null) review++;
    }
    return new QuizAttemptResult(
        attempt.getPublicId(),
        set.getPublicId(),
        attempt.getStatus(),
        false,
        new QuizAttemptSummary(
            new GradingCount(correct, graded),
            new EssaySelfAssessmentSummary(essayCorrect, partial, essayIncorrect),
            review),
        projected);
  }

  private QuizQuestionResultView question(QuizAttemptQuestion aq) {
    QuizQuestion q = questions.findById(aq.getQuestionId()).orElseThrow();
    List<QuizSubmittedAnswer> values = submitted.findAllByAttemptQuestionIdOrderById(aq.getId());
    List<QuizQuestionChoice> cs = choices.findAllByQuestionIdOrderById(q.getId());
    List<QuizFillInTheBlank> bs = blanks.findAllByQuestionIdOrderByNumber(q.getId());
    List<ChoiceView> choiceViews =
        cs.stream().map(c -> new ChoiceView(c.getPublicId(), c.getValue())).toList();
    List<BlankView> blankViews =
        bs.stream().map(b -> new BlankView(b.getPublicId(), b.getNumber())).toList();
    Object response = null, representative = null;
    switch (q.getType()) {
      case MULTIPLE_CHOICE -> {
        QuizSubmittedAnswer value = values.stream().findFirst().orElse(null);
        if (value != null) {
          QuizQuestionChoice c = choices.findById(value.getSelectedChoiceId()).orElseThrow();
          response = new SelectedChoiceAnswer(c.getPublicId());
        }
        representative =
            cs.stream()
                .filter(QuizQuestionChoice::isCorrect)
                .findFirst()
                .map(c -> new SelectedChoiceAnswer(c.getPublicId()))
                .orElse(null);
      }
      case SHORT_ANSWER -> {
        if (!values.isEmpty()) response = new AnswerValue(values.getFirst().getValue());
        List<QuizShortAnswerAnswer> accepted = shorts.findAllByQuestionIdOrderById(q.getId());
        if (!accepted.isEmpty()) representative = new AnswerValue(accepted.getFirst().getValue());
      }
      case FILL_IN_THE_BLANK -> {
        if (!values.isEmpty())
          response =
              new BlankAnswersValue(
                  values.stream()
                      .map(
                          v ->
                              new BlankAnswerValue(
                                  blanks.findById(v.getBlankId()).orElseThrow().getPublicId(),
                                  v.getValue()))
                      .toList());
        representative =
            new BlankAnswersValue(
                bs.stream()
                    .map(
                        b ->
                            new BlankAnswerValue(
                                b.getPublicId(),
                                blankAnswers
                                    .findAllByBlankIdOrderById(b.getId())
                                    .getFirst()
                                    .getValue()))
                    .toList());
      }
      case ESSAY -> {
        if (!values.isEmpty()) response = new AnswerValue(values.getFirst().getValue());
        QuizEssayAnswerGuide guide = essays.findById(q.getId()).orElseThrow();
        representative = new EssayAnswerValue(guide.getModelAnswer(), guide.getKeyPoints());
      }
    }
    return new QuizQuestionResultView(
        q.getPublicId(),
        q.getNumber(),
        q.getType(),
        q.getTopic(),
        q.getPrompt(),
        choiceViews.isEmpty() ? null : choiceViews,
        blankViews.isEmpty() ? null : blankViews,
        response,
        representative,
        aq.getFinalGradingResult(),
        q.getExplanation(),
        q.getSourceExcerpt());
  }
}
