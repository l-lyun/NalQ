package com.openmd.server.quiz.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.entity.QuizQuestionResult;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizQuestionResultRepository;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class ShortAnswerGradingService {

	private final QuizAttemptRepository attempts;
	private final QuizQuestionRepository questions;
	private final QuizQuestionResultRepository results;
	private final QuizAttemptResultProjector projector;

	public ShortAnswerGradingService(
		QuizAttemptRepository attempts,
		QuizQuestionRepository questions,
		QuizQuestionResultRepository results,
		QuizAttemptResultProjector projector
	) {
		this.attempts = attempts;
		this.questions = questions;
		this.results = results;
		this.projector = projector;
	}

	@Transactional
	public QuizAttemptResult update(
		long userId,
		String attemptPublicId,
		String questionPublicId,
		String requestedOutcome
	) {
		GradingOutcome outcome = parseOutcome(requestedOutcome);
		GradingTarget target = target(userId, attemptPublicId, questionPublicId);
		validate(target);
		if (target.result().overrideWith(outcome)) {
			results.flush();
		}
		return projector.project(target.attempt());
	}

	private GradingTarget target(long userId, String attemptPublicId, String questionPublicId) {
		QuizAttempt attempt = attempts.findOwnedForUpdate(attemptPublicId, userId)
			.orElseThrow(this::notFound);
		QuizQuestion question = questions.findByPublicIdAndQuizSetId(questionPublicId, attempt.getQuizSetId())
			.orElseThrow(this::notFound);
		QuizQuestionResult result = results.findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
			.orElseThrow(this::notFound);
		return new GradingTarget(attempt, question, result);
	}

	private void validate(GradingTarget target) {
		if (target.attempt().getStatus() != QuizAttemptStatus.COMPLETED
			|| target.question().getType() != QuestionType.SHORT_ANSWER
			|| !target.result().isAnswered()) {
			throw new BusinessException(QuizErrorCode.ATTEMPT_CONFLICT);
		}
	}

	private GradingOutcome parseOutcome(String value) {
		try {
			return GradingOutcome.valueOf(value);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new BusinessException(
				CommonErrorCode.INVALID_INPUT,
				List.of(new FieldError("outcome", "outcome은 CORRECT 또는 INCORRECT여야 합니다."))
			);
		}
	}

	private BusinessException notFound() {
		return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
	}

	private record GradingTarget(
		QuizAttempt attempt,
		QuizQuestion question,
		QuizQuestionResult result
	) {
	}
}
