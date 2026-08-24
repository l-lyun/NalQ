package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.ReviewSessionQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewSessionQuestionRepository extends JpaRepository<ReviewSessionQuestion, Long> {
	List<ReviewSessionQuestion> findAllByReviewSessionIdOrderBySequenceNumber(long reviewSessionId);
}
