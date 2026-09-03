package com.openmd.server.quiz.service;

import com.openmd.server.global.error.*;
import com.openmd.server.notification.domain.QuizGenerationNotification;
import com.openmd.server.notification.repository.NotificationRepository;
import com.openmd.server.quiz.domain.*;
import com.openmd.server.quiz.domain.entity.*;
import com.openmd.server.quiz.domain.type.*;
import com.openmd.server.quiz.repository.*;
import java.time.Instant;
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
  private final NotificationRepository notifications;

  public QuizGenerationPersistenceService(
      QuizSetRepository sets,
      QuizQuestionRepository questions,
      QuizQuestionChoiceRepository choices,
      QuizShortAnswerAnswerRepository shorts,
      QuizEssayAnswerGuideRepository essays,
      QuizFillInTheBlankRepository blanks,
      QuizFillInTheBlankAnswerRepository blankAnswers,
      NotificationRepository notifications) {
    this.sets = sets;
    this.questions = questions;
    this.choices = choices;
    this.shorts = shorts;
    this.essays = essays;
    this.blanks = blanks;
    this.blankAnswers = blankAnswers;
    this.notifications = notifications;
  }

  @Transactional
  public int complete(
      long userId,
      String quizSetId,
      List<QuizGenerationCandidate> candidates,
      int maxQuestionCount) {
    QuizSet set =
        sets.findOwnedForUpdate(quizSetId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (set.getStatus() != QuizSetStatus.GENERATING) return 0;
    if (maxQuestionCount < 1) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT,
          List.of(new com.openmd.server.global.api.FieldError("maxQuestionCount", "1 이상이어야 합니다.")));
    }
    List<ValidatedQuizQuestion> valid =
        validator.validateAll(candidates).stream().limit(maxQuestionCount).toList();
    int minimumValidQuestionCount =
        Math.toIntExact((maxQuestionCount * 4L + 4L) / 5L);
    if (valid.size() < minimumValidQuestionCount) {
      set.fail(QuizSetFailureCode.SOURCE_INSUFFICIENT);
      notifications.save(QuizGenerationNotification.from(set));
      return 0;
    }
    for (ValidatedQuizQuestion value : valid) persist(set.getId(), value);
    set.ready();
    notifications.save(QuizGenerationNotification.from(set));
    return valid.size();
  }

  @Transactional
  public void failGeneration(long userId, String quizSetId) {
    failGeneration(userId, quizSetId, QuizSetFailureCode.GENERATION_FAILED);
  }

  @Transactional
  public void failGeneration(long userId, String quizSetId, QuizSetFailureCode failureCode) {
    QuizSet set =
        sets.findOwnedForUpdate(quizSetId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (set.getStatus() == QuizSetStatus.GENERATING) {
      set.fail(failureCode);
      notifications.save(QuizGenerationNotification.from(set));
    }
  }

  @Transactional
  public boolean markStarted(long userId, String quizSetId) {
    QuizSet set =
        sets.findOwnedForUpdate(quizSetId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    if (set.getStatus() != QuizSetStatus.GENERATING) return false;
    set.markGenerationStarted(Instant.now());
    return true;
  }

  @Transactional
  public int failInterruptedGenerations() {
    List<QuizSet> interrupted = sets.findAllByStatus(QuizSetStatus.GENERATING);
    interrupted.forEach(
        set -> {
          set.fail(QuizSetFailureCode.GENERATION_FAILED);
          notifications.save(QuizGenerationNotification.from(set));
        });
    return interrupted.size();
  }

  @Transactional
  public List<String> failStaleGenerations(Instant cutoff) {
    List<QuizSet> stale =
        sets.findStaleForUpdate(QuizSetStatus.GENERATING, cutoff);
    stale.forEach(
        set -> {
          set.fail(QuizSetFailureCode.GENERATION_FAILED);
          notifications.save(QuizGenerationNotification.from(set));
        });
    return stale.stream().map(QuizSet::getPublicId).toList();
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
