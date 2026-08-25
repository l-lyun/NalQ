package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizFillInTheBlankAnswer;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizFillInTheBlankAnswerRepository
    extends JpaRepository<QuizFillInTheBlankAnswer, Long> {
  List<QuizFillInTheBlankAnswer> findAllByBlankIdOrderById(long blankId);
}
