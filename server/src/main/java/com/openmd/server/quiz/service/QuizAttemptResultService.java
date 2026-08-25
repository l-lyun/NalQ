package com.openmd.server.quiz.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptResultService {

	private final QuizAttemptRepository attempts;
	private final QuizAttemptResultProjector projector;

	public QuizAttemptResultService(QuizAttemptRepository attempts, QuizAttemptResultProjector projector) {
		this.attempts = attempts;
		this.projector = projector;
	}

	@Transactional(readOnly = true)
	public QuizAttemptResult result(long userId, String attemptPublicId) {
		var attempt = attempts.findByPublicIdAndUserId(attemptPublicId, userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return projector.project(attempt);
	}
}
