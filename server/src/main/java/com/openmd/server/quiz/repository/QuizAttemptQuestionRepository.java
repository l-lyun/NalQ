package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizAttemptQuestion;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface QuizAttemptQuestionRepository extends JpaRepository<QuizAttemptQuestion, Long> {
  List<QuizAttemptQuestion> findAllByAttemptIdOrderBySequenceNumber(long attemptId);

  Optional<QuizAttemptQuestion> findByAttemptIdAndQuestionId(long attemptId, long questionId);

  @Query(
      "select q from QuizAttemptQuestion q where q.attemptId=:attemptId and q.finalGradingResult in"
          + " :outcomes and q.reviewResolvedAt is null order by q.sequenceNumber")
  List<QuizAttemptQuestion> findReviewCandidates(
      @Param("attemptId") long attemptId, @Param("outcomes") Collection<GradingOutcome> outcomes);

  @Query(
      "select q.attemptId as attemptId, count(q) as reviewQuestionCount"
          + " from QuizAttemptQuestion q where q.attemptId in :attemptIds"
          + " and q.finalGradingResult in :outcomes and q.reviewResolvedAt is null"
          + " group by q.attemptId")
  List<ReviewCandidateCount> countReviewCandidatesByAttemptIdIn(
      @Param("attemptIds") Collection<Long> attemptIds,
      @Param("outcomes") Collection<GradingOutcome> outcomes);
}
