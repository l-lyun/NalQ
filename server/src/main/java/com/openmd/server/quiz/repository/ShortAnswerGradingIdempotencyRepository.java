package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.ShortAnswerGradingIdempotency;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortAnswerGradingIdempotencyRepository extends JpaRepository<ShortAnswerGradingIdempotency, Long> {

	Optional<ShortAnswerGradingIdempotency> findByUserIdAndAttemptIdAndQuestionIdAndIdempotencyKeyHash(
		long userId, long attemptId, long questionId, byte[] idempotencyKeyHash
	);
}
