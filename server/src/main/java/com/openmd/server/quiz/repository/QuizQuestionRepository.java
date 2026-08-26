package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizQuestion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

  List<QuizQuestion> findAllByQuizSetIdOrderByNumber(long quizSetId);

  long countByQuizSetId(long quizSetId);

  Optional<QuizQuestion> findByPublicIdAndQuizSetId(String publicId, long quizSetId);
}
