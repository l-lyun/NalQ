package com.openmd.server.quiz.service;

import com.openmd.server.global.error.*;
import com.openmd.server.quiz.domain.entity.*;
import com.openmd.server.quiz.domain.type.*;
import com.openmd.server.quiz.dto.response.*;
import com.openmd.server.quiz.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "openmd.quiz.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class QuizSetQueryService {
  private final QuizSetRepository sets;
  private final QuizQuestionRepository questions;
  private final QuizQuestionChoiceRepository choices;
  private final QuizFillInTheBlankRepository blanks;

  public QuizSetQueryService(
      QuizSetRepository sets,
      QuizQuestionRepository questions,
      QuizQuestionChoiceRepository choices,
      QuizFillInTheBlankRepository blanks) {
    this.sets = sets;
    this.questions = questions;
    this.choices = choices;
    this.blanks = blanks;
  }

  @Transactional(readOnly = true)
  public QuizSetView get(long userId, String publicId) {
    QuizSet set =
        sets.findByPublicIdAndUserId(publicId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    List<QuizQuestionView> qs =
        set.getStatus() == QuizSetStatus.READY
            ? questions.findAllByQuizSetIdOrderByNumber(set.getId()).stream()
                .map(this::view)
                .toList()
            : null;
    QuizFailureView failure =
        set.getStatus() == QuizSetStatus.FAILED ? failure(set.getFailureCode()) : null;
    return new QuizSetView(
        set.getPublicId(),
        Long.toString(set.getLearningMaterialId()),
        set.getStatus(),
        set.getStatus() == QuizSetStatus.GENERATING ? 3 : null,
        qs,
        failure);
  }

  private QuizQuestionView view(QuizQuestion q) {
    List<ChoiceView> cs =
        q.getType() == QuestionType.MULTIPLE_CHOICE
            ? choices.findAllByQuestionIdOrderById(q.getId()).stream()
                .map(c -> new ChoiceView(c.getPublicId(), c.getValue()))
                .toList()
            : null;
    List<BlankView> bs =
        q.getType() == QuestionType.FILL_IN_THE_BLANK
            ? blanks.findAllByQuestionIdOrderByNumber(q.getId()).stream()
                .map(b -> new BlankView(b.getPublicId(), b.getNumber()))
                .toList()
            : null;
    return new QuizQuestionView(
        q.getPublicId(), q.getNumber(), q.getType(), q.getTopic(), q.getPrompt(), cs, bs);
  }

  private QuizFailureView failure(QuizSetFailureCode code) {
    return code == QuizSetFailureCode.SOURCE_INSUFFICIENT
        ? new QuizFailureView(code, "학습자료에서 문제를 만들지 못했어요. 자료나 조건을 확인해 주세요.", false)
        : new QuizFailureView(code, "문제 생성에 실패했어요. 다시 시도해 주세요.", true);
  }
}
