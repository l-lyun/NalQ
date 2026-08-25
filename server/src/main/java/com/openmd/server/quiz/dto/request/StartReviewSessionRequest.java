package com.openmd.server.quiz.dto.request;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import java.util.List;

public record StartReviewSessionRequest(String sourceAttemptId) {
  public String requiredSourceAttemptId() {
    if (sourceAttemptId == null || sourceAttemptId.isBlank()) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT,
          List.of(new FieldError("sourceAttemptId", "sourceAttemptId가 필요합니다.")));
    }
    return sourceAttemptId;
  }
}
