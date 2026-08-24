package com.openmd.server.quiz.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.QuizAttempt;
import com.openmd.server.quiz.domain.QuizAttemptStatus;
import com.openmd.server.quiz.domain.ReviewSession;
import com.openmd.server.quiz.domain.ReviewSessionQuestion;
import com.openmd.server.quiz.dto.response.CreatedReviewSnapshot;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizQuestionResultRepository;
import com.openmd.server.quiz.repository.ReviewSessionQuestionRepository;
import com.openmd.server.quiz.repository.ReviewSessionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class ReviewSessionSnapshotService {

	private final QuizAttemptRepository attempts;
	private final QuizQuestionResultRepository results;
	private final ReviewSessionRepository sessions;
	private final ReviewSessionQuestionRepository sessionQuestions;

	public ReviewSessionSnapshotService(
		QuizAttemptRepository attempts,
		QuizQuestionResultRepository results,
		ReviewSessionRepository sessions,
		ReviewSessionQuestionRepository sessionQuestions
	) {
		this.attempts = attempts;
		this.results = results;
		this.sessions = sessions;
		this.sessionQuestions = sessionQuestions;
	}

	@Transactional
	public CreatedReviewSnapshot createForAttempt(long userId, String attemptPublicId) {
		QuizAttempt attempt = attempts.findOwnedForUpdate(attemptPublicId, userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		if (attempt.getStatus() != QuizAttemptStatus.COMPLETED) {
			throw new BusinessException(QuizErrorCode.ATTEMPT_CONFLICT);
		}
		var candidates = results.findReviewCandidateQuestionIds(attempt.getId());
		ReviewSession session = sessions.saveAndFlush(
			ReviewSession.active(userId, attempt.getId(), attempt.getSummaryRevision())
		);
		for (int index = 0; index < candidates.size(); index++) {
			sessionQuestions.save(ReviewSessionQuestion.pending(session.getId(), candidates.get(index), index + 1));
		}
		return new CreatedReviewSnapshot(session.getPublicId(), session.getSourceSummaryRevision(), candidates.size());
	}
}
