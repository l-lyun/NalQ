package com.openmd.server.quiz.repository;

import com.openmd.server.quiz.domain.entity.QuizQuestionResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizQuestionResultRepository extends JpaRepository<QuizQuestionResult, Long> {

	Optional<QuizQuestionResult> findByAttemptIdAndQuestionId(long attemptId, long questionId);

	List<QuizQuestionResult> findAllByAttemptId(long attemptId);

	@Query("""
		select count(r) from QuizQuestionResult r
		join QuizQuestion q on q.id = r.questionId
		where r.attemptId = :attemptId
		and q.type in (com.openmd.server.quiz.domain.type.QuestionType.MULTIPLE_CHOICE,
			com.openmd.server.quiz.domain.type.QuestionType.FILL_IN_THE_BLANK,
			com.openmd.server.quiz.domain.type.QuestionType.SHORT_ANSWER)
		and coalesce(r.userOverrideOutcome, r.automaticOutcome) = com.openmd.server.quiz.domain.type.GradingOutcome.CORRECT
		""")
	long countCurrentCorrect(@Param("attemptId") long attemptId);

	@Query("""
		select count(r) from QuizQuestionResult r
		join QuizQuestion q on q.id = r.questionId
		where r.attemptId = :attemptId
		and q.type in (com.openmd.server.quiz.domain.type.QuestionType.MULTIPLE_CHOICE,
			com.openmd.server.quiz.domain.type.QuestionType.FILL_IN_THE_BLANK,
			com.openmd.server.quiz.domain.type.QuestionType.SHORT_ANSWER)
		""")
	long countGraded(@Param("attemptId") long attemptId);

	@Query("""
		select count(r) from QuizQuestionResult r
		where r.attemptId = :attemptId and r.reviewResolved = false
		and coalesce(r.userOverrideOutcome, r.automaticOutcome) = com.openmd.server.quiz.domain.type.GradingOutcome.INCORRECT
		""")
	long countReviewRequired(@Param("attemptId") long attemptId);

	@Query("""
		select r.questionId from QuizQuestionResult r
		join QuizQuestion q on q.id = r.questionId
		where r.attemptId = :attemptId and r.reviewResolved = false
		and coalesce(r.userOverrideOutcome, r.automaticOutcome) = com.openmd.server.quiz.domain.type.GradingOutcome.INCORRECT
		order by q.number
		""")
	List<Long> findReviewCandidateQuestionIds(@Param("attemptId") long attemptId);
}
