package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.QuizAttemptSubmission;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptSubmissionRepository extends JpaRepository<QuizAttemptSubmission, Long> {

	Optional<QuizAttemptSubmission> findByUserIdAndQuizSetIdAndIdempotencyKeyHash(
		long userId, long quizSetId, byte[] idempotencyKeyHash
	);
}
