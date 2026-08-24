package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.QuizSet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select q from QuizSet q where q.publicId = :publicId and q.userId = :userId")
	Optional<QuizSet> findOwnedForUpdate(@Param("publicId") String publicId, @Param("userId") long userId);
}
