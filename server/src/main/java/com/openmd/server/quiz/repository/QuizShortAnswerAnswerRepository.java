package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizShortAnswerAnswer;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizShortAnswerAnswerRepository
    extends JpaRepository<QuizShortAnswerAnswer, Long> {
  List<QuizShortAnswerAnswer> findAllByQuestionIdOrderById(long questionId);
}
