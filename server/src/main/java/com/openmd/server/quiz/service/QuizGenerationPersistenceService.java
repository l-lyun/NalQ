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

  /** TODO: 외부 문제 생성 모델 연동이 완료되면 이 임시 고정 후보 생성 경로를 제거한다. */
  @Transactional
  public void completeWithTemporaryStub(
      long userId,
      String quizSetId,
      List<QuestionType> selectedTypes,
      int maxQuestionCount) {
    complete(
        userId,
        quizSetId,
        selectedTypes.stream().map(this::temporaryCandidate).toList(),
        maxQuestionCount);
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
    if (set.getStatus() != QuizSetStatus.GENERATING)
      throw new IllegalStateException("Quiz set is already finalized");
    if (maxQuestionCount < 1) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT,
          List.of(new com.openmd.server.global.api.FieldError("maxQuestionCount", "1 이상이어야 합니다.")));
    }
    List<ValidatedQuizQuestion> valid =
        validator.validateAll(candidates).stream().limit(maxQuestionCount).toList();
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

  @Transactional
  public int failInterruptedGenerations() {
    List<QuizSet> interrupted = sets.findAllByStatus(QuizSetStatus.GENERATING);
    interrupted.forEach(set -> set.fail(QuizSetFailureCode.GENERATION_FAILED));
    return interrupted.size();
  }

  private QuizGenerationCandidate temporaryCandidate(QuestionType type) {
    return switch (type) {
      case MULTIPLE_CHOICE ->
          new QuizGenerationCandidate(
              null,
              type,
              "임시 객관식",
              "NalQ의 임시 퀴즈 정답을 고르세요.",
              "외부 모델 연동 전 사용하는 임시 해설입니다.",
              "외부 모델 연동 전 사용하는 임시 원문 근거입니다.",
              List.of(
                  new QuizGenerationCandidate.ChoiceCandidate("정답", true),
                  new QuizGenerationCandidate.ChoiceCandidate("오답 1", false),
                  new QuizGenerationCandidate.ChoiceCandidate("오답 2", false)),
              List.of(),
              List.of(),
              null,
              List.of());
      case FILL_IN_THE_BLANK ->
          new QuizGenerationCandidate(
              null,
              type,
              "임시 빈칸",
              "NalQ의 임시 빈칸 정답은 [1]입니다.",
              "외부 모델 연동 전 사용하는 임시 해설입니다.",
              "외부 모델 연동 전 사용하는 임시 원문 근거입니다.",
              List.of(),
              List.of(),
              List.of(new QuizGenerationCandidate.BlankCandidate(1, List.of("정답"))),
              null,
              List.of());
      case SHORT_ANSWER ->
          new QuizGenerationCandidate(
              null,
              type,
              "임시 단답형",
              "NalQ의 임시 단답형 정답을 입력하세요.",
              "외부 모델 연동 전 사용하는 임시 해설입니다.",
              "외부 모델 연동 전 사용하는 임시 원문 근거입니다.",
              List.of(),
              List.of("정답"),
              List.of(),
              null,
              List.of());
      case ESSAY ->
          new QuizGenerationCandidate(
              null,
              type,
              "임시 서술형",
              "NalQ의 임시 서술형 답안을 작성하세요.",
              "외부 모델 연동 전 사용하는 임시 해설입니다.",
              "외부 모델 연동 전 사용하는 임시 원문 근거입니다.",
              List.of(),
              List.of(),
              List.of(),
              "외부 모델 연동 전 사용하는 임시 모범 답안입니다.",
              List.of("임시 핵심 포인트"));
    };
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
