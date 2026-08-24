package com.openmd.server.quiz.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.entity.QuizQuestionResult;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.dto.response.AnswerValue;
import com.openmd.server.quiz.dto.response.EssaySelfAssessmentSummary;
import com.openmd.server.quiz.dto.response.GradingCount;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.dto.response.QuizAttemptSummary;
import com.openmd.server.quiz.dto.response.ShortAnswerQuestionResult;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizQuestionResultRepository;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptResultProjector {

	private final QuizSetRepository quizSets;
	private final QuizQuestionRepository questions;
	private final QuizQuestionResultRepository results;

	public QuizAttemptResultProjector(
		QuizSetRepository quizSets,
		QuizQuestionRepository questions,
		QuizQuestionResultRepository results
	) {
		this.quizSets = quizSets;
		this.questions = questions;
		this.results = results;
	}

	public QuizAttemptResult project(QuizAttempt attempt) {
		var quizSet = quizSets.findById(attempt.getQuizSetId())
			.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		return new QuizAttemptResult(
			attempt.getPublicId(), quizSet.getPublicId(), attempt.getStatus(),
			summary(attempt), questionResults(attempt)
		);
	}

	private QuizAttemptSummary summary(QuizAttempt attempt) {
		return new QuizAttemptSummary(
			new GradingCount(
				Math.toIntExact(results.countCurrentCorrect(attempt.getId())),
				Math.toIntExact(results.countGraded(attempt.getId()))
			),
			new EssaySelfAssessmentSummary(0, 0, 0),
			Math.toIntExact(results.countReviewRequired(attempt.getId()))
		);
	}

	private List<ShortAnswerQuestionResult> questionResults(QuizAttempt attempt) {
		List<QuizQuestion> quizQuestions = questions.findAllByQuizSetIdOrderByNumber(attempt.getQuizSetId());
		Map<Long, QuizQuestionResult> byQuestion = new HashMap<>();
		for (QuizQuestionResult result : results.findAllByAttemptId(attempt.getId())) {
			byQuestion.put(result.getQuestionId(), result);
		}
		return quizQuestions.stream().map(question -> projectQuestion(attempt, question, byQuestion)).toList();
	}

	private ShortAnswerQuestionResult projectQuestion(
		QuizAttempt attempt,
		QuizQuestion question,
		Map<Long, QuizQuestionResult> byQuestion
	) {
		if (question.getType() != QuestionType.SHORT_ANSWER) {
			throw new IllegalStateException("Non-short-answer projection belongs to its dedicated implementation");
		}
		QuizQuestionResult result = byQuestion.get(question.getId());
		if (result == null) {
			throw new IllegalStateException("Attempt result is incomplete: " + attempt.getPublicId());
		}
		return new ShortAnswerQuestionResult(
			question.getPublicId(), question.getNumber(), question.getType(), question.getTopic(), question.getPrompt(),
			result.isAnswered() ? new AnswerValue(result.getSubmittedAnswer()) : null,
			new AnswerValue(question.getRepresentativeAnswer()), result.currentOutcome(),
			question.getExplanation(), question.getSourceExcerpt()
		);
	}
}
