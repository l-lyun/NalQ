package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizAttempt;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

	Optional<QuizAttempt> findByPublicIdAndUserId(String publicId, long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from QuizAttempt a where a.publicId = :publicId and a.userId = :userId")
	Optional<QuizAttempt> findOwnedForUpdate(@Param("publicId") String publicId, @Param("userId") long userId);
}
