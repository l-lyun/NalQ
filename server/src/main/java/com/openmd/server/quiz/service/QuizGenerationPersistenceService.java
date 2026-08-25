package com.openmd.server.quiz.service;

import com.openmd.server.global.error.*;
import com.openmd.server.quiz.domain.*;
import com.openmd.server.quiz.domain.entity.*;
import com.openmd.server.quiz.domain.type.*;
import com.openmd.server.quiz.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "openmd.quiz.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class QuizGenerationPersistenceService {
  private final QuizGenerationCandidateValidator validator = new QuizGenerationCandidateValidator();
  private final QuizSetRepository sets;
  private final QuizQuestionRepository questions;
  private final QuizQuestionChoiceRepository choices;
  private final QuizShortAnswerAnswerRepository shorts;
  private final QuizEssayAnswerGuideRepository essays;
  private final QuizFillInTheBlankRepository blanks;
  private final QuizFillInTheBlankAnswerRepository blankAnswers;

  public QuizGenerationPersistenceService(
      QuizSetRepository sets,
      QuizQuestionRepository questions,
      QuizQuestionChoiceRepository choices,
      QuizShortAnswerAnswerRepository shorts,
      QuizEssayAnswerGuideRepository essays,
      QuizFillInTheBlankRepository blanks,
      QuizFillInTheBlankAnswerRepository blankAnswers) {
    this.sets = sets;
    this.questions = questions;
    this.choices = choices;
    this.shorts = shorts;
    this.essays = essays;
    this.blanks = blanks;
    this.blankAnswers = blankAnswers;
  }

  @Transactional
  public int complete(long userId, String quizSetId, List<QuizGenerationCandidate> candidates) {
    QuizSet set =
        sets.findOwnedForUpdate(quizSetId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (set.getStatus() != QuizSetStatus.GENERATING)
      throw new IllegalStateException("Quiz set is already finalized");
    List<ValidatedQuizQuestion> valid = validator.validateAll(candidates);
    if (valid.isEmpty()) {
      set.fail(QuizSetFailureCode.SOURCE_INSUFFICIENT);
      return 0;
    }
    for (ValidatedQuizQuestion value : valid) persist(set.getId(), value);
    set.ready();
    return valid.size();
  }

  @Transactional
  public void failGeneration(long userId, String quizSetId) {
    QuizSet set =
        sets.findOwnedForUpdate(quizSetId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (set.getStatus() == QuizSetStatus.GENERATING) set.fail(QuizSetFailureCode.GENERATION_FAILED);
  }

  private void persist(long setId, ValidatedQuizQuestion validated) {
    QuizGenerationCandidate c = validated.candidate();
    QuizQuestion q =
        questions.saveAndFlush(
            QuizQuestion.create(
                setId,
                validated.number(),
                c.type(),
                c.topic(),
                c.prompt(),
                c.explanation(),
                c.sourceExcerpt()));
    switch (c.type()) {
      case MULTIPLE_CHOICE ->
          choices.saveAll(
              c.choices().stream()
                  .map(v -> QuizQuestionChoice.of(q.getId(), v.text(), v.correct()))
                  .toList());
      case SHORT_ANSWER ->
          shorts.saveAll(
              c.acceptedAnswers().stream()
                  .map(v -> QuizShortAnswerAnswer.of(q.getId(), v))
                  .toList());
      case ESSAY -> essays.save(QuizEssayAnswerGuide.of(q.getId(), c.modelAnswer(), c.keyPoints()));
      case FILL_IN_THE_BLANK -> {
        for (var source : c.blanks()) {
          QuizFillInTheBlank blank =
              blanks.saveAndFlush(QuizFillInTheBlank.of(q.getId(), source.number()));
          blankAnswers.saveAll(
              source.acceptedAnswers().stream()
                  .map(v -> QuizFillInTheBlankAnswer.of(blank.getId(), v))
                  .toList());
        }
      }
    }
  }
}
