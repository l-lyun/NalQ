package com.openmd.server.quiz.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.dto.command.QuizGenerationConfig;
import com.openmd.server.quiz.dto.response.AcceptedQuizGeneration;
import com.openmd.server.quiz.dto.response.ActiveQuizGeneration;
import com.openmd.server.quiz.dto.response.RequestedQuizConfig;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizGenerationService {
  private static final int POLL_AFTER_SECONDS = 3;
  private static final Set<Integer> ALLOWED_COUNTS = Set.of(5, 10, 15, 20);
  private static final String ACTIVE_CONSTRAINT = "uk_quiz_sets_active_generation_user";

  private final LearningMaterialRepository materials;
  private final QuizSetRepository quizSets;
  private final QuizGenerationAcceptanceTransaction acceptance;
  private final QuizGenerationCapacity capacity;

  public QuizGenerationService(
      LearningMaterialRepository materials,
      QuizSetRepository quizSets,
      QuizGenerationAcceptanceTransaction acceptance,
      QuizGenerationCapacity capacity) {
    this.materials = materials;
    this.quizSets = quizSets;
    this.acceptance = acceptance;
    this.capacity = capacity;
  }

  public AcceptedQuizGeneration accept(
      long userId, String materialPublicId, QuizGenerationConfig requestedConfig) {
    long materialId = materialId(materialPublicId);
    QuizGenerationConfig config = validated(requestedConfig);
    if (!capacity.tryAcquire()) {
      throw new BusinessException(QuizErrorCode.GENERATION_UNAVAILABLE);
    }
    QuizSet quizSet;
    try {
      quizSet = acceptance.accept(userId, materialId, config);
    } catch (DataIntegrityViolationException exception) {
      capacity.release();
      if (hasConstraint(exception, ACTIVE_CONSTRAINT)) {
        throw new BusinessException(QuizErrorCode.GENERATION_ACTIVE);
      }
      throw exception;
    } catch (RuntimeException exception) {
      capacity.release();
      throw exception;
    }
    return new AcceptedQuizGeneration(
        quizSet.getPublicId(),
        materialPublicId,
        quizSet.getQuizTitle(),
        quizSet.getStatus(),
        POLL_AFTER_SECONDS,
        new RequestedQuizConfig(
            config.selectedTypes(), config.difficulty(), config.maxQuestionCount()),
        quizSet.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public ActiveQuizGeneration active(long userId, String materialPublicId) {
    long materialId = materialId(materialPublicId);
    materials
        .findByIdAndUserId(materialId, userId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    QuizSet quizSet =
        quizSets
            .findFirstByLearningMaterialIdAndUserIdAndStatusOrderByCreatedAtDesc(
                materialId, userId, QuizSetStatus.GENERATING)
            .orElse(null);
    return quizSet == null
        ? null
        : new ActiveQuizGeneration(
            quizSet.getPublicId(),
            materialPublicId,
            quizSet.getQuizTitle(),
            quizSet.getStatus(),
            POLL_AFTER_SECONDS);
  }

  private QuizGenerationConfig validated(QuizGenerationConfig config) {
    if (config == null) throw invalid("request", "생성 조건이 필요합니다.");
    List<QuestionType> types = config.selectedTypes();
    if (types == null || types.isEmpty() || types.stream().anyMatch(java.util.Objects::isNull)) {
      throw invalid("selectedTypes", "selectedTypes는 하나 이상이어야 합니다.");
    }
    if (new HashSet<>(types).size() != types.size()) {
      throw invalid("selectedTypes", "selectedTypes에는 중복이 없어야 합니다.");
    }
    if (config.difficulty() == null) throw invalid("difficulty", "difficulty가 필요합니다.");
    if (config.maxQuestionCount() == null || !ALLOWED_COUNTS.contains(config.maxQuestionCount())) {
      throw invalid("maxQuestionCount", "maxQuestionCount는 5, 10, 15, 20 중 하나여야 합니다.");
    }
    String prompt = config.generationPrompt() == null ? null : config.generationPrompt().strip();
    if (prompt != null && prompt.codePointCount(0, prompt.length()) > 300) {
      throw invalid("generationPrompt", "generationPrompt는 300자 이하여야 합니다.");
    }
    if (prompt != null && prompt.isEmpty()) prompt = null;
    return new QuizGenerationConfig(
        List.copyOf(types), config.difficulty(), config.maxQuestionCount(), prompt);
  }

  private boolean hasConstraint(Throwable exception, String constraint) {
    for (Throwable current = exception; current != null; current = current.getCause()) {
      if (current.getMessage() != null && current.getMessage().contains(constraint)) return true;
    }
    return false;
  }

  private long materialId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) throw new NumberFormatException();
      return id;
    } catch (NumberFormatException | NullPointerException exception) {
      throw invalid("materialId", "materialId가 올바르지 않습니다.");
    }
  }

  private BusinessException invalid(String field, String reason) {
    return new BusinessException(
        CommonErrorCode.INVALID_INPUT, List.of(new FieldError(field, reason)));
  }
}
