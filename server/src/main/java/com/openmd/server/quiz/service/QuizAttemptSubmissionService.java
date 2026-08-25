package com.openmd.server.quiz.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.ShortAnswerGrader;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.entity.QuizQuestionResult;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.dto.model.QuizAttemptSubmissionResult;
import com.openmd.server.quiz.dto.request.QuizResponseRequest;
import com.openmd.server.quiz.dto.response.GradingCount;
import com.openmd.server.quiz.dto.response.SubmittedQuizAttempt;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizQuestionResultRepository;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptSubmissionService {

	private final QuizSetRepository quizSets;
	private final QuizQuestionRepository questions;
	private final QuizAttemptRepository attempts;
	private final QuizQuestionResultRepository results;

	public QuizAttemptSubmissionService(
		QuizSetRepository quizSets,
		QuizQuestionRepository questions,
		QuizAttemptRepository attempts,
		QuizQuestionResultRepository results
	) {
		this.quizSets = quizSets;
		this.questions = questions;
		this.attempts = attempts;
		this.results = results;
	}

	@Transactional
	public QuizAttemptSubmissionResult submit(
		long userId,
		String quizSetPublicId,
		String requestedAttemptId,
		List<QuizResponseRequest> submittedResponses
	) {
		String attemptId = canonicalAttemptId(requestedAttemptId);
		QuizSet quizSet = quizSets.findOwnedForUpdate(quizSetPublicId, userId).orElseThrow(this::notFound);
		QuizAttempt existing = attempts.findByPublicId(attemptId).orElse(null);
		if (existing != null) {
			return existingSubmission(userId, quizSet, existing);
		}
		if (quizSet.getStatus() != QuizSetStatus.READY) {
			throw conflict();
		}
		if (submittedResponses == null) {
			throw invalid("responses", "responses가 필요합니다.");
		}

		List<QuizQuestion> quizQuestions = questions.findAllByQuizSetIdOrderByNumber(quizSet.getId());
		Map<String, QuizResponseRequest> byQuestion = validateAndIndex(submittedResponses, quizQuestions);
		GradedSubmission graded = grade(quizQuestions, byQuestion);
		QuizAttempt attempt = save(attemptId, userId, quizSet, graded);
		return new QuizAttemptSubmissionResult(true, response(attempt));
	}

	private QuizAttemptSubmissionResult existingSubmission(long userId, QuizSet quizSet, QuizAttempt existing) {
		if (existing.getUserId() != userId || existing.getQuizSetId() != quizSet.getId()) {
			throw attemptIdConflict();
		}
		return new QuizAttemptSubmissionResult(false, response(existing));
	}

	private QuizAttempt save(
		String attemptId,
		long userId,
		QuizSet quizSet,
		GradedSubmission graded
	) {
		QuizAttempt attempt;
		try {
			attempt = attempts.saveAndFlush(QuizAttempt.completed(
				attemptId, quizSet.getId(), userId, graded.correctCount(), graded.results().size()
			));
		} catch (DataIntegrityViolationException exception) {
			throw attemptIdConflict();
		}
		results.saveAll(graded.results().stream()
			.map(pending -> QuizQuestionResult.automatic(
				attempt.getId(), pending.question().getId(), pending.answer(), pending.outcome()
			))
			.toList());
		return attempt;
	}

	private GradedSubmission grade(
		List<QuizQuestion> quizQuestions,
		Map<String, QuizResponseRequest> byQuestion
	) {
		List<PendingResult> pending = quizQuestions.stream().map(question -> {
			if (question.getType() != QuestionType.SHORT_ANSWER) {
				throw invalid("responses", "현재 제출 경계는 단답형 문제만 지원합니다.");
			}
			QuizResponseRequest response = byQuestion.get(question.getPublicId());
			String answer = response == null ? null : writtenText(response.text());
			return new PendingResult(
				question, answer, ShortAnswerGrader.grade(answer, question.acceptedAnswerValues())
			);
		}).toList();
		int correctCount = (int) pending.stream()
			.filter(value -> value.outcome() == GradingOutcome.CORRECT)
			.count();
		return new GradedSubmission(pending, correctCount);
	}

	private Map<String, QuizResponseRequest> validateAndIndex(
		List<QuizResponseRequest> submittedResponses,
		List<QuizQuestion> quizQuestions
	) {
		Set<String> known = quizQuestions.stream()
			.map(QuizQuestion::getPublicId)
			.collect(java.util.stream.Collectors.toSet());
		Set<String> seen = new HashSet<>();
		Map<String, QuizResponseRequest> indexed = new HashMap<>();
		for (QuizResponseRequest response : submittedResponses) {
			if (response == null || response.questionId() == null || !known.contains(response.questionId())
				|| !seen.add(response.questionId())) {
				throw invalid("responses", "알 수 없거나 중복된 questionId가 있습니다.");
			}
			if (response.selectedChoiceId() != null || response.blankAnswers() != null) {
				throw invalid("responses", "문제 유형과 답안 모양이 일치하지 않습니다.");
			}
			indexed.put(response.questionId(), response);
		}
		return indexed;
	}

	private SubmittedQuizAttempt response(QuizAttempt attempt) {
		return new SubmittedQuizAttempt(
			attempt.getPublicId(), attempt.getStatus(),
			new GradingCount(attempt.getAutomaticCorrectCount(), attempt.getAutomaticGradedCount()),
			List.of(), databaseTimestamp(attempt.getCreatedAt())
		);
	}

	private Instant databaseTimestamp(Instant value) {
		return value == null ? null : value.plusNanos(500).truncatedTo(ChronoUnit.MICROS);
	}

	private String canonicalAttemptId(String value) {
		try {
			UUID parsed = UUID.fromString(value);
			String canonical = parsed.toString();
			if (!canonical.equalsIgnoreCase(value)) {
				throw invalidAttemptId();
			}
			return canonical;
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw invalidAttemptId();
		}
	}

	private String writtenText(String text) {
		if (text == null || text.codePoints().allMatch(codePoint ->
			Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint))) {
			return null;
		}
		return text;
	}

	private BusinessException invalidAttemptId() {
		return invalid("attemptId", "attemptId는 UUID 형식이어야 합니다.");
	}

	private BusinessException invalid(String field, String reason) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, List.of(new FieldError(field, reason)));
	}

	private BusinessException notFound() {
		return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
	}

	private BusinessException conflict() {
		return new BusinessException(QuizErrorCode.ATTEMPT_CONFLICT);
	}

	private BusinessException attemptIdConflict() {
		return new BusinessException(
			QuizErrorCode.ATTEMPT_CONFLICT,
			List.of(new FieldError("attemptId", "이미 다른 사용자 또는 문제 세트에서 사용한 식별자입니다."))
		);
	}

	private record PendingResult(QuizQuestion question, String answer, GradingOutcome outcome) {
	}

	private record GradedSubmission(List<PendingResult> results, int correctCount) {
	}
}
