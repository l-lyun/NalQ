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
import java.util.ArrayList;
import java.util.List;
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
            .findFirstByUserIdAndTypeAndStatusOrderByCompletedAtDesc(
                userId, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED)
            .orElse(null);
    if (main == null) return new ReviewLatestView(null, null, null, null, null, 0, 0, null);
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
        material.getTitle(),
        main.getCompletedAt(),
        totalQuestionCount,
        count,
        active);
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
            .findTopByUserIdAndTypeAndStatusOrderByCompletedAtDesc(
                userId, QuizAttemptType.MAIN, QuizAttemptStatus.COMPLETED)
            .orElseThrow(() -> new BusinessException(QuizErrorCode.REVIEW_UNAVAILABLE));
    if (!requestedSourceAttemptId.equals(main.getPublicId())) {
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
