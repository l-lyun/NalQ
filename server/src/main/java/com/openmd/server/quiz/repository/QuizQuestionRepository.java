package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizQuestion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

  List<QuizQuestion> findAllByQuizSetIdOrderByNumber(long quizSetId);

  long countByQuizSetId(long quizSetId);

  @Query(
      "select q.quizSetId as quizSetId, count(q) as questionCount from QuizQuestion q"
          + " where q.quizSetId in :quizSetIds group by q.quizSetId")
  List<QuizSetQuestionCount> countByQuizSetIdIn(
      @Param("quizSetIds") Collection<Long> quizSetIds);

  Optional<QuizQuestion> findByPublicIdAndQuizSetId(String publicId, long quizSetId);
}
