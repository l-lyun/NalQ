package com.openmd.server.quiz.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.QuizTitlePolicy;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.dto.response.RenamedQuizSet;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizSetManagementService {
  private final QuizSetRepository sets;

  public QuizSetManagementService(QuizSetRepository sets) {
    this.sets = sets;
  }

  @Transactional
  public RenamedQuizSet rename(long userId, String quizSetId, String requestedTitle) {
    String title;
    try {
      title = QuizTitlePolicy.normalize(requestedTitle);
    } catch (IllegalArgumentException exception) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT,
          List.of(new FieldError("quizTitle", "quizTitle은 1~255자여야 합니다.")));
    }
    QuizSet set =
        sets.findOwnedForUpdate(quizSetId, userId)
            .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    set.rename(title);
    sets.flush();
    return new RenamedQuizSet(set.getPublicId(), set.getQuizTitle(), set.getUpdatedAt());
  }
}
