package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizSubmittedAnswer;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizSubmittedAnswerRepository extends JpaRepository<QuizSubmittedAnswer, Long> {
  List<QuizSubmittedAnswer> findAllByAttemptQuestionIdOrderById(long attemptQuestionId);

  boolean existsByAttemptQuestionId(long attemptQuestionId);
}
