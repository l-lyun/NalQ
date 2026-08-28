package com.openmd.server.quiz.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizAttemptQuestion;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizAttemptType;
import com.openmd.server.quiz.dto.response.BlankView;
import com.openmd.server.quiz.dto.response.ChoiceView;
import com.openmd.server.quiz.dto.response.QuizQuestionView;
import com.openmd.server.quiz.dto.response.ReviewCandidateItem;
import com.openmd.server.quiz.dto.response.ReviewCandidateList;
import com.openmd.server.quiz.dto.response.ReviewLatestView;
import com.openmd.server.quiz.dto.response.ReviewSessionStart;
import com.openmd.server.quiz.dto.response.ReviewSessionView;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptQuestionRepository;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizFillInTheBlankRepository;
import com.openmd.server.quiz.repository.QuizQuestionChoiceRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizSetRepository;
import com.openmd.server.quiz.repository.ReviewCandidateCount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizReviewService {
  private static final List<GradingOutcome> REVIEW_OUTCOMES =
      List.of(GradingOutcome.INCORRECT, GradingOutcome.PARTIAL);

  private final QuizAttemptRepository attempts;
  private final QuizAttemptQuestionRepository attemptQuestions;
  private final QuizSetRepository sets;
  private final LearningMaterialRepository materials;
  private final QuizQuestionRepository questions;
  private final QuizQuestionChoiceRepository choices;
  private final QuizFillInTheBlankRepository blanks;

  public QuizReviewService(
      QuizAttemptRepository attempts,
      QuizAttemptQuestionRepository attemptQuestions,
      QuizSetRepository sets,
      LearningMaterialRepository materials,
      QuizQuestionRepository questions,
      QuizQuestionChoiceRepository choices,
      QuizFillInTheBlankRepository blanks) {
    this.attempts = attempts;
    this.attemptQuestions = attemptQuestions;
    this.sets = sets;
    this.materials = materials;
    this.questions = questions;
    this.choices = choices;
    this.blanks = blanks;
  }

  @Transactional(readOnly = true)
  public ReviewLatestView latest(long userId) {
    QuizAttempt main =
        attempts
            .findFirstByUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
                userId, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED)
            .orElse(null);
    if (main == null) return new ReviewLatestView(null, null, null, null, null, null, 0, 0, null);
    QuizSet set = sets.findById(main.getQuizSetId()).orElseThrow();
    LearningMaterial material =
        materials.findByIdAndUserId(set.getLearningMaterialId(), userId).orElseThrow();
    int totalQuestionCount = Math.toIntExact(questions.countByQuizSetId(set.getId()));
    int count = attemptQuestions.findReviewCandidates(main.getId(), REVIEW_OUTCOMES).size();
    String active =
        attempts
            .findFirstByUserIdAndSourceAttemptIdAndStatusNotOrderByCreatedAtDesc(
                userId, main.getId(), QuizAttemptStatus.COMPLETED)
            .map(QuizAttempt::getPublicId)
            .orElse(null);
    int attemptNumber =
        Math.toIntExact(
            attempts.countByQuizSetIdAndUserIdAndTypeAndStatus(
                main.getQuizSetId(), userId, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED));
    return new ReviewLatestView(
        main.getPublicId(),
        set.getPublicId(),
        attemptNumber,
        set.getQuizTitle(),
        material.getTitle(),
        main.getCompletedAt(),
        totalQuestionCount,
        count,
        active);
  }

  @Transactional(readOnly = true)
  public ReviewCandidateList candidates(long userId, int limit) {
    if (limit < 1 || limit > 3) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT,
          List.of(new FieldError("limit", "limit은 1 이상 3 이하여야 합니다.")));
    }

    List<QuizSet> ownedSets = sets.findAllByUserId(userId);
    if (ownedSets.isEmpty()) return new ReviewCandidateList(List.of());

    List<Long> setIds = ownedSets.stream().map(QuizSet::getId).toList();
    Map<Long, QuizAttempt> completedBySet = new HashMap<>();
    Map<Long, QuizAttempt> pendingBySet = new HashMap<>();
    Map<Long, QuizAttempt> activeReviewBySource = new HashMap<>();
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
        activeReviewBySource.merge(
            attempt.getSourceAttemptId(), attempt, this::newerByUpdatedAt);
      }
    }

    QuizAttempt globalLatest =
        completedBySet.values().stream().reduce(this::newerByCompletedAt).orElse(null);
    if (globalLatest == null) return new ReviewCandidateList(List.of());

    List<Long> completedAttemptIds =
        ownedSets.stream()
            .map(set -> completedBySet.get(set.getId()))
            .filter(java.util.Objects::nonNull)
            .map(QuizAttempt::getId)
            .toList();
    Map<Long, Integer> reviewCountsByAttempt = new HashMap<>();
    if (!completedAttemptIds.isEmpty()) {
      for (ReviewCandidateCount count :
          attemptQuestions.countReviewCandidatesByAttemptIdIn(
              completedAttemptIds, REVIEW_OUTCOMES)) {
        reviewCountsByAttempt.put(
            count.getAttemptId(), Math.toIntExact(count.getReviewQuestionCount()));
      }
    }

    List<QuizSet> candidateSets =
        ownedSets.stream()
            .filter(set -> set.getId() != globalLatest.getQuizSetId())
            .filter(
                set -> {
                  QuizAttempt completed = completedBySet.get(set.getId());
                  return completed != null
                      && reviewCountsByAttempt.getOrDefault(completed.getId(), 0) > 0;
                })
            .toList();
    if (candidateSets.isEmpty()) return new ReviewCandidateList(List.of());

    List<Long> materialIds =
        candidateSets.stream().map(QuizSet::getLearningMaterialId).distinct().toList();
    Map<Long, LearningMaterial> materialsById = new HashMap<>();
    for (LearningMaterial material : materials.findAllByIdInAndUserId(materialIds, userId)) {
      materialsById.put(material.getId(), material);
    }

    Comparator<ReviewCandidateItem> order =
        Comparator.comparing(
                (ReviewCandidateItem candidate) -> candidate.activeReviewSessionId() != null)
            .reversed()
            .thenComparing(
                ReviewCandidateItem::lastLearningActivityAt,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ReviewCandidateItem::quizSetId);
    List<ReviewCandidateItem> items =
        candidateSets.stream()
            .map(
                set -> {
                  QuizAttempt completed = completedBySet.get(set.getId());
                  QuizAttempt pending = pendingBySet.get(set.getId());
                  QuizAttempt active = activeReviewBySource.get(completed.getId());
                  QuizAttempt lastActivity = lastActivityBySet.get(set.getId());
                  LearningMaterial material =
                      Optional.ofNullable(materialsById.get(set.getLearningMaterialId()))
                          .orElseThrow();
                  return new ReviewCandidateItem(
                      set.getPublicId(),
                      set.getQuizTitle(),
                      material.getTitle(),
                      completed.getPublicId(),
                      pending == null ? null : pending.getPublicId(),
                      active == null ? null : active.getPublicId(),
                      reviewCountsByAttempt.get(completed.getId()),
                      lastActivity == null ? null : lastActivity.getUpdatedAt());
                })
            .sorted(order)
            .limit(limit)
            .toList();
    return new ReviewCandidateList(items);
  }

  @Transactional
  public ReviewSessionStart start(long userId, String requestedSourceAttemptId) {
    if (requestedSourceAttemptId == null || requestedSourceAttemptId.isBlank()) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT,
          List.of(new FieldError("sourceAttemptId", "sourceAttemptId가 필요합니다.")));
    }
    QuizAttempt main =
        attempts
            .findOwnedForUpdate(requestedSourceAttemptId, userId)
            .filter(attempt -> attempt.getType() == QuizAttemptType.MAIN)
            .filter(attempt -> attempt.getStatus() == QuizAttemptStatus.COMPLETED)
            .orElseThrow(() -> new BusinessException(QuizErrorCode.REVIEW_UNAVAILABLE));
    QuizAttempt latest =
        attempts
            .findFirstByQuizSetIdAndUserIdAndTypeAndStatusOrderByCompletedAtDescIdDesc(
                main.getQuizSetId(), userId, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED)
            .orElseThrow(() -> new BusinessException(QuizErrorCode.REVIEW_UNAVAILABLE));
    if (!main.getId().equals(latest.getId())) {
      throw new BusinessException(QuizErrorCode.REVIEW_UNAVAILABLE);
    }
    QuizAttempt active =
        attempts
            .findFirstByUserIdAndSourceAttemptIdAndStatusNotOrderByCreatedAtDesc(
                userId, main.getId(), QuizAttemptStatus.COMPLETED)
            .orElse(null);
    if (active != null) return new ReviewSessionStart(false, view(active));
    List<QuizAttemptQuestion> candidates =
        attemptQuestions.findReviewCandidates(main.getId(), REVIEW_OUTCOMES);
    if (candidates.isEmpty()) throw new BusinessException(QuizErrorCode.REVIEW_UNAVAILABLE);

    QuizAttempt review =
        attempts.saveAndFlush(QuizAttempt.review(main.getQuizSetId(), userId, main.getId()));
    for (int index = 0; index < candidates.size(); index++) {
      QuizAttemptQuestion source = candidates.get(index);
      attemptQuestions.save(
          QuizAttemptQuestion.review(
              review.getId(), source.getQuestionId(), source.getId(), index + 1));
    }
    return new ReviewSessionStart(true, view(review));
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
      java.time.Instant currentAt,
      java.time.Instant candidateAt) {
    int timeComparison = candidateAt.compareTo(currentAt);
    if (timeComparison > 0) return candidate;
    if (timeComparison < 0) return current;
    return candidate.getId() > current.getId() ? candidate : current;
  }

  @Transactional(readOnly = true)
  public ReviewSessionView get(long userId, String id) {
    QuizAttempt review =
        attempts
            .findByPublicIdAndUserId(id, userId)
            .filter(attempt -> attempt.getType() == QuizAttemptType.REVIEW)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    return view(review);
  }

  private ReviewSessionView view(QuizAttempt review) {
    QuizAttempt source = attempts.findById(review.getSourceAttemptId()).orElseThrow();
    List<QuizQuestionView> views = new ArrayList<>();
    List<String> pendingEssayQuestionIds = new ArrayList<>();
    for (QuizAttemptQuestion row :
        attemptQuestions.findAllByAttemptIdOrderBySequenceNumber(review.getId())) {
      QuizQuestionView view = question(row.getQuestionId());
      views.add(view);
      if (review.getStatus() == QuizAttemptStatus.SELF_ASSESSMENT_REQUIRED
          && view.type() == QuestionType.ESSAY
          && row.getFinalGradingResult() == null) {
        pendingEssayQuestionIds.add(view.questionId());
      }
    }
    return new ReviewSessionView(
        review.getPublicId(),
        source.getPublicId(),
        publicStatus(review.getStatus()),
        views.size(),
        List.copyOf(pendingEssayQuestionIds),
        List.copyOf(views));
  }

  private String publicStatus(QuizAttemptStatus status) {
    return status == QuizAttemptStatus.IN_PROGRESS ? "SOLVING" : status.name();
  }

  private QuizQuestionView question(long id) {
    QuizQuestion question = questions.findById(id).orElseThrow();
    List<ChoiceView> choiceViews =
        question.getType() == QuestionType.MULTIPLE_CHOICE
            ? choices.findAllByQuestionIdOrderById(id).stream()
                .map(choice -> new ChoiceView(choice.getPublicId(), choice.getValue()))
                .toList()
            : null;
    List<BlankView> blankViews =
        question.getType() == QuestionType.FILL_IN_THE_BLANK
            ? blanks.findAllByQuestionIdOrderByNumber(id).stream()
                .map(blank -> new BlankView(blank.getPublicId(), blank.getNumber()))
                .toList()
            : null;
    return new QuizQuestionView(
        question.getPublicId(),
        question.getNumber(),
        question.getType(),
        question.getTopic(),
        question.getPrompt(),
        choiceViews,
        blankViews);
  }
}
