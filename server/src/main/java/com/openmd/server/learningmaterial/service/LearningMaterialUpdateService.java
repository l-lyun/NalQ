package com.openmd.server.learningmaterial.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.dto.request.UpdateLearningMaterialRequest;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialDetail;
import com.openmd.server.learningmaterial.error.LearningMaterialErrorCode;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(
    name = "openmd.learning-material.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LearningMaterialUpdateService {
  private static final int MAX_TITLE_CODE_POINTS = 255;
  private static final int MAX_CONTENT_CODE_POINTS = 20_000;
  private final LearningMaterialRepository materials;
  private final QuizSetRepository quizSets;

  public LearningMaterialUpdateService(
      LearningMaterialRepository materials, QuizSetRepository quizSets) {
    this.materials = materials;
    this.quizSets = quizSets;
  }

  @Transactional
  public LearningMaterialDetail update(
      long userId, long materialId, UpdateLearningMaterialRequest request) {
    if (materialId < 1) throw invalid("materialId", "materialId는 양의 정수여야 합니다.");
    if (request == null || (request.title() == null && request.content() == null)) {
      throw invalid("request", "수정할 title 또는 content가 필요합니다.");
    }
    String title = request.title() == null ? null : validTitle(request.title());
    String content = request.content() == null ? null : validContent(request.content());
    LearningMaterial material =
        materials
            .findOwnedForUpdate(materialId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    boolean generating =
        quizSets.existsByLearningMaterialIdAndUserIdAndStatus(
            materialId, userId, QuizSetStatus.GENERATING);
    if (content != null && generating) {
      throw new BusinessException(LearningMaterialErrorCode.CONTENT_LOCKED);
    }
    if (title != null) material.updateTitle(title);
    if (content != null) material.updateContent(content);
    materials.flush();
    return new LearningMaterialDetail(
        Long.toString(materialId),
        material.getTitle(),
        material.getContent(),
        codePointCount(material.getContent()),
        material.getSourceType(),
        generating ? ContentEditStatus.LOCKED_GENERATING : ContentEditStatus.EDITABLE,
        material.getCreatedAt(),
        material.getUpdatedAt());
  }

  private String validTitle(String value) {
    String title = trimUnicodeWhitespace(value);
    if (title.isEmpty()) throw invalid("title", "제목을 입력해야 합니다.");
    if (codePointCount(title) > MAX_TITLE_CODE_POINTS) {
      throw invalid("title", "제목은 255자를 초과할 수 없습니다.");
    }
    return title;
  }

  private String validContent(String value) {
    if (value == null || value.codePoints().allMatch(this::isUnicodeWhitespace)) {
      throw invalid("content", "본문을 입력해야 합니다.");
    }
    if (codePointCount(value) > MAX_CONTENT_CODE_POINTS) {
      throw new BusinessException(
          LearningMaterialErrorCode.CONTENT_TOO_LONG,
          List.of(new FieldError("content", "본문은 20,000자를 초과할 수 없습니다.")));
    }
    return value;
  }

  private String trimUnicodeWhitespace(String value) {
    if (value == null || value.isEmpty()) return "";
    int start = 0;
    int end = value.length();
    while (start < end) {
      int cp = value.codePointAt(start);
      if (!isUnicodeWhitespace(cp)) break;
      start += Character.charCount(cp);
    }
    while (end > start) {
      int cp = value.codePointBefore(end);
      if (!isUnicodeWhitespace(cp)) break;
      end -= Character.charCount(cp);
    }
    return value.substring(start, end);
  }

  private boolean isUnicodeWhitespace(int cp) {
    return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
  }

  private int codePointCount(String value) {
    return value.codePointCount(0, value.length());
  }

  private BusinessException invalid(String field, String reason) {
    return new BusinessException(
        CommonErrorCode.INVALID_INPUT, List.of(new FieldError(field, reason)));
  }
}
