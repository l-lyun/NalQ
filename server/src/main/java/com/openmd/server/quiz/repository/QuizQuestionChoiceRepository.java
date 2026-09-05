package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizQuestionChoice;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizQuestionChoiceRepository extends JpaRepository<QuizQuestionChoice, Long> {
  List<QuizQuestionChoice> findAllByQuestionIdOrderById(long questionId);

  Optional<QuizQuestionChoice> findByPublicId(String publicId);
}
