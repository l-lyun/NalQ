package com.openmd.server.quiz.service;

import com.openmd.server.global.error.*;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.entity.*;
import com.openmd.server.quiz.domain.type.*;
import com.openmd.server.quiz.dto.response.*;
import com.openmd.server.quiz.repository.*;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  private final LearningMaterialRepository materials;
  private final QuizAttemptRepository attempts;
  private final QuizAttemptQuestionRepository attemptQuestions;

  public QuizSetQueryService(
      QuizSetRepository sets,
      QuizQuestionRepository questions,
      QuizQuestionChoiceRepository choices,
      QuizFillInTheBlankRepository blanks,
      LearningMaterialRepository materials,
      QuizAttemptRepository attempts,
      QuizAttemptQuestionRepository attemptQuestions) {
    this.sets = sets;
    this.questions = questions;
    this.choices = choices;
    this.blanks = blanks;
    this.materials = materials;
    this.attempts = attempts;
    this.attemptQuestions = attemptQuestions;
  }

  @Transactional(readOnly = true)
  public QuizSetPage list(long userId, int page, int size, String query) {
    if (page < 1) throw invalid("page", "page는 1 이상이어야 합니다.");
    if (size < 1 || size > 20) throw invalid("size", "size는 1 이상 20 이하여야 합니다.");
    String normalizedQuery = trimUnicodeWhitespace(query);
    PageRequest pageable =
        PageRequest.of(
            page - 1,
            size,
            Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("publicId")));
    Page<QuizSet> result =
        normalizedQuery.isEmpty()
            ? sets.findAllByUserId(userId, pageable)
            : sets.findAllByUserIdAndQuizTitleContainingIgnoreCase(
                userId, normalizedQuery, pageable);
    List<QuizSetListItem> items = result.getContent().stream().map(set -> item(userId, set)).toList();
    return new QuizSetPage(items, page, size, result.getTotalElements(), result.getTotalPages());
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
        set.getQuizTitle(),
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

  private QuizSetListItem item(long userId, QuizSet set) {
    LearningMaterial material =
        materials.findByIdAndUserId(set.getLearningMaterialId(), userId).orElseThrow();
    QuizAttempt completed =
        attempts
            .findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDesc(
                set.getId(), userId, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED)
            .orElse(null);
    QuizAttempt pending =
        attempts
            .findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByUpdatedAtDesc(
                set.getId(),
                userId,
                QuizAttemptType.MAIN,
                QuizAttemptStatus.SELF_ASSESSMENT_REQUIRED)
            .orElse(null);
    QuizAttempt activeReview =
        attempts
            .findFirstByQuizSetIdAndUserIdAndTypeAndStatusNotOrderByUpdatedAtDesc(
                set.getId(), userId, QuizAttemptType.REVIEW, QuizAttemptStatus.COMPLETED)
            .orElse(null);
    QuizAttempt lastActivity =
        attempts.findFirstByQuizSetIdAndUserIdOrderByUpdatedAtDesc(set.getId(), userId).orElse(null);
    int reviewCount =
        completed == null
            ? 0
            : attemptQuestions
                .findReviewCandidates(
                    completed.getId(), List.of(GradingOutcome.INCORRECT, GradingOutcome.PARTIAL))
                .size();
    Integer questionCount =
        set.getStatus() == QuizSetStatus.READY
            ? Math.toIntExact(questions.countByQuizSetId(set.getId()))
            : null;
    return new QuizSetListItem(
        set.getPublicId(),
        set.getQuizTitle(),
        Long.toString(set.getLearningMaterialId()),
        material.getTitle(),
        set.getStatus(),
        questionCount,
        set.getCreatedAt(),
        set.getUpdatedAt(),
        completed == null ? null : completed.getPublicId(),
        pending == null ? null : pending.getPublicId(),
        activeReview == null ? null : activeReview.getPublicId(),
        reviewCount,
        lastActivity == null ? null : lastActivity.getUpdatedAt());
  }

  private String trimUnicodeWhitespace(String value) {
    if (value == null || value.isEmpty()) return "";
    int start = 0;
    int end = value.length();
    while (start < end) {
      int cp = value.codePointAt(start);
      if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) break;
      start += Character.charCount(cp);
    }
    while (end > start) {
      int cp = value.codePointBefore(end);
      if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) break;
      end -= Character.charCount(cp);
    }
    return value.substring(start, end);
  }

  private BusinessException invalid(String field, String reason) {
    return new BusinessException(
        CommonErrorCode.INVALID_INPUT,
        List.of(new com.openmd.server.global.api.FieldError(field, reason)));
  }
}
