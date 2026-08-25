package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizFillInTheBlank;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizFillInTheBlankRepository extends JpaRepository<QuizFillInTheBlank, Long> {
  List<QuizFillInTheBlank> findAllByQuestionIdOrderByNumber(long questionId);

  Optional<QuizFillInTheBlank> findByPublicId(String publicId);
}
