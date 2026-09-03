package com.openmd.server.quiz.service;

import com.openmd.server.global.error.*;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.entity.*;
import com.openmd.server.quiz.domain.type.*;
import com.openmd.server.quiz.dto.response.*;
import com.openmd.server.quiz.repository.*;
import java.time.Instant;
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
    return list(userId, page, size, query, null);
  }

  @Transactional(readOnly = true)
  public QuizSetPage list(
      long userId, int page, int size, String query, String focusQuizSetId) {
    if (page < 1) throw invalid("page", "page는 1 이상이어야 합니다.");
    if (size < 1 || size > 20) throw invalid("size", "size는 1 이상 20 이하여야 합니다.");
    String normalizedQuery = trimUnicodeWhitespace(query);
    String normalizedFocus = trimUnicodeWhitespace(focusQuizSetId);
    if (!normalizedQuery.isEmpty() && !normalizedFocus.isEmpty()) {
      throw invalid("focusQuizSetId", "focusQuizSetId는 query와 함께 사용할 수 없습니다.");
    }
    int resolvedPage =
        normalizedFocus.isEmpty()
            ? page
            : focusPage(userId, normalizedFocus, size);
    PageRequest pageable =
        PageRequest.of(
            resolvedPage - 1,
            size,
            Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("publicId")));
    Page<QuizSet> result =
        normalizedQuery.isEmpty()
            ? sets.findAllByUserIdAndStatusNot(userId, QuizSetStatus.FAILED, pageable)
            : sets.findAllByUserIdAndStatusNotAndQuizTitleContainingIgnoreCase(
                userId, QuizSetStatus.FAILED, normalizedQuery, pageable);
    List<QuizSet> pageSets = result.getContent();
    List<QuizSetListItem> items = listItems(userId, pageSets);
    return new QuizSetPage(
        items, resolvedPage, size, result.getTotalElements(), result.getTotalPages());
  }

  private int focusPage(long userId, String publicId, int size) {
    QuizSet target =
        sets.findByPublicIdAndUserId(publicId, userId)
            .filter(set -> set.getStatus() == QuizSetStatus.READY)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    long newer =
        sets.countVisibleBeforeFocus(
            userId, QuizSetStatus.FAILED, target.getUpdatedAt(), target.getPublicId());
    return Math.toIntExact(newer / size) + 1;
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

  private List<QuizSetListItem> listItems(long userId, List<QuizSet> pageSets) {
    if (pageSets.isEmpty()) return List.of();

    List<Long> setIds = pageSets.stream().map(QuizSet::getId).toList();
    List<Long> materialIds =
        pageSets.stream().map(QuizSet::getLearningMaterialId).distinct().toList();
    Map<Long, LearningMaterial> materialsById = new HashMap<>();
    for (LearningMaterial material : materials.findAllByIdInAndUserId(materialIds, userId)) {
      materialsById.put(material.getId(), material);
    }

    Map<Long, QuizAttempt> completedBySet = new HashMap<>();
    Map<Long, QuizAttempt> pendingBySet = new HashMap<>();
    Map<Long, QuizAttempt> activeReviewBySet = new HashMap<>();
    Map<Long, QuizAttempt> lastActivityBySet = new HashMap<>();
    for (QuizAttempt attempt : attempts.findAllByQuizSetIdInAndUserId(setIds, userId)) {
      lastActivityBySet.merge(attempt.getQuizSetId(), attempt, this::newerByUpdatedAt);
      if (attempt.getType() == QuizAttemptType.MAIN
          && attempt.getStatus() == QuizAttemptStatus.COMPLETED) {
        completedBySet.merge(attempt.getQuizSetId(), attempt, this::newerByCompletedAt);
      }
      if (attempt.getType() == QuizAttemptType.MAIN
          && attempt.getStatus() == QuizAttemptStatus.SELF_ASSESSMENT_REQUIRED) {
        pendingBySet.merge(attempt.getQuizSetId(), attempt, this::newerByUpdatedAt);
      }
      if (attempt.getType() == QuizAttemptType.REVIEW
          && attempt.getStatus() != QuizAttemptStatus.COMPLETED) {
        activeReviewBySet.merge(attempt.getQuizSetId(), attempt, this::newerByUpdatedAt);
      }
    }

    Map<Long, Integer> questionCountsBySet = new HashMap<>();
    for (QuizSetQuestionCount count : questions.countByQuizSetIdIn(setIds)) {
      questionCountsBySet.put(count.getQuizSetId(), Math.toIntExact(count.getQuestionCount()));
    }

    List<Long> completedAttemptIds =
        completedBySet.values().stream().map(QuizAttempt::getId).toList();
    Map<Long, Integer> reviewCountsByAttempt = new HashMap<>();
    if (!completedAttemptIds.isEmpty()) {
      for (ReviewCandidateCount count :
          attemptQuestions.countReviewCandidatesByAttemptIdIn(
              completedAttemptIds, List.of(GradingOutcome.INCORRECT, GradingOutcome.PARTIAL))) {
        reviewCountsByAttempt.put(
            count.getAttemptId(), Math.toIntExact(count.getReviewQuestionCount()));
      }
    }

    return pageSets.stream()
        .map(
            set ->
                item(
                    set,
                    materialsById,
                    completedBySet,
                    pendingBySet,
                    activeReviewBySet,
                    lastActivityBySet,
                    questionCountsBySet,
                    reviewCountsByAttempt))
        .toList();
  }

  private QuizSetListItem item(
      QuizSet set,
      Map<Long, LearningMaterial> materialsById,
      Map<Long, QuizAttempt> completedBySet,
      Map<Long, QuizAttempt> pendingBySet,
      Map<Long, QuizAttempt> activeReviewBySet,
      Map<Long, QuizAttempt> lastActivityBySet,
      Map<Long, Integer> questionCountsBySet,
      Map<Long, Integer> reviewCountsByAttempt) {
    LearningMaterial material =
        Optional.ofNullable(materialsById.get(set.getLearningMaterialId())).orElseThrow();
    QuizAttempt completed = completedBySet.get(set.getId());
    QuizAttempt pending = pendingBySet.get(set.getId());
    QuizAttempt activeReview = activeReviewBySet.get(set.getId());
    QuizAttempt lastActivity = lastActivityBySet.get(set.getId());
    int reviewCount =
        completed == null ? 0 : reviewCountsByAttempt.getOrDefault(completed.getId(), 0);
    Integer questionCount =
        set.getStatus() == QuizSetStatus.READY
            ? questionCountsBySet.getOrDefault(set.getId(), 0)
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

  private QuizAttempt newerByUpdatedAt(QuizAttempt current, QuizAttempt candidate) {
    return newer(current, candidate, current.getUpdatedAt(), candidate.getUpdatedAt());
  }

  private QuizAttempt newerByCompletedAt(QuizAttempt current, QuizAttempt candidate) {
    return newer(current, candidate, current.getCompletedAt(), candidate.getCompletedAt());
  }

  private QuizAttempt newer(
      QuizAttempt current,
      QuizAttempt candidate,
      Instant currentAt,
      Instant candidateAt) {
    int timeComparison = candidateAt.compareTo(currentAt);
    if (timeComparison > 0) return candidate;
    if (timeComparison < 0) return current;
    return candidate.getId() > current.getId() ? candidate : current;
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
