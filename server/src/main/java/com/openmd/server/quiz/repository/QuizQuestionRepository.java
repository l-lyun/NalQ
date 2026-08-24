package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.QuizQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

	@EntityGraph(attributePaths = "acceptedAnswers")
	List<QuizQuestion> findAllByQuizSetIdOrderByNumber(long quizSetId);

	@EntityGraph(attributePaths = "acceptedAnswers")
	Optional<QuizQuestion> findByPublicIdAndQuizSetId(String publicId, long quizSetId);
}
