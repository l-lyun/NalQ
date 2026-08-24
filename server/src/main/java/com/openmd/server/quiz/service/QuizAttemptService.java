package com.openmd.server.quiz.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.quiz.domain.ShortAnswerGrader;
import com.openmd.server.quiz.domain.entity.QuizAttempt;
import com.openmd.server.quiz.domain.entity.QuizAttemptSubmission;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.entity.QuizQuestionResult;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.entity.ShortAnswerGradingIdempotency;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.dto.request.QuizResponseRequest;
import com.openmd.server.quiz.dto.response.AnswerValue;
import com.openmd.server.quiz.dto.response.EssaySelfAssessmentSummary;
import com.openmd.server.quiz.dto.response.GradingCount;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.dto.response.QuizAttemptSummary;
import com.openmd.server.quiz.dto.response.ShortAnswerGradingSummary;
import com.openmd.server.quiz.dto.response.ShortAnswerQuestionResult;
import com.openmd.server.quiz.dto.response.SubmittedQuizAttempt;
import com.openmd.server.quiz.dto.response.UpdatedShortAnswerGrading;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.repository.QuizAttemptRepository;
import com.openmd.server.quiz.repository.QuizAttemptSubmissionRepository;
import com.openmd.server.quiz.repository.QuizQuestionRepository;
import com.openmd.server.quiz.repository.QuizQuestionResultRepository;
import com.openmd.server.quiz.repository.QuizSetRepository;
import com.openmd.server.quiz.repository.ShortAnswerGradingIdempotencyRepository;
import com.openmd.server.quiz.util.QuizRequestDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptService {

	private final QuizSetRepository quizSets;
	private final QuizQuestionRepository questions;
	private final QuizAttemptRepository attempts;
	private final QuizQuestionResultRepository results;
	private final QuizAttemptSubmissionRepository submissions;
	private final ShortAnswerGradingIdempotencyRepository gradingIdempotencies;
	private final EntityManager entityManager;
	private final Clock clock;

	public QuizAttemptService(
		QuizSetRepository quizSets,
		QuizQuestionRepository questions,
		QuizAttemptRepository attempts,
		QuizQuestionResultRepository results,
		QuizAttemptSubmissionRepository submissions,
		ShortAnswerGradingIdempotencyRepository gradingIdempotencies,
		EntityManager entityManager
	) {
		this.quizSets = quizSets;
		this.questions = questions;
		this.attempts = attempts;
		this.results = results;
		this.submissions = submissions;
		this.gradingIdempotencies = gradingIdempotencies;
		this.entityManager = entityManager;
		this.clock = Clock.systemUTC();
	}

	@Transactional
	public SubmittedQuizAttempt submit(
		long userId,
		String quizSetPublicId,
		String idempotencyKey,
		List<QuizResponseRequest> submittedResponses
	) {
		byte[] keyHash = QuizRequestDigest.idempotencyKey(idempotencyKey);
		if (submittedResponses == null) {
			throw invalid("responses", "responses가 필요합니다.");
		}
		QuizSet quizSet = quizSets.findOwnedForUpdate(quizSetPublicId, userId).orElseThrow(this::notFound);
		if (quizSet.getStatus() != QuizSetStatus.READY) {
			throw conflict();
		}
		byte[] fingerprint = submissionFingerprint(submittedResponses);
		var replay = submissions.findByUserIdAndQuizSetIdAndIdempotencyKeyHash(
			userId, quizSet.getId(), keyHash
		);
		if (replay.isPresent()) {
			if (!Arrays.equals(fingerprint, replay.get().getRequestFingerprint())) {
				throw conflict();
			}
			QuizAttempt existing = attempts.findById(replay.get().getAttemptId()).orElseThrow(this::notFound);
			return submittedResponse(existing);
		}

		List<QuizQuestion> quizQuestions = questions.findAllByQuizSetIdOrderByNumber(quizSet.getId());
		Map<String, QuizResponseRequest> byQuestion = validateAndIndexResponses(submittedResponses, quizQuestions);
		List<PendingResult> pending = quizQuestions.stream().map(question -> {
			if (question.getType() != QuestionType.SHORT_ANSWER) {
				throw invalid("responses", "현재 제출 경계는 단답형 문제만 지원합니다.");
			}
			QuizResponseRequest response = byQuestion.get(question.getPublicId());
			String answer = response == null ? null : writtenText(response.text());
			GradingOutcome outcome = ShortAnswerGrader.grade(answer, question.acceptedAnswerValues());
			return new PendingResult(question, answer, outcome);
		}).toList();
		int correct = (int) pending.stream().filter(value -> value.outcome() == GradingOutcome.CORRECT).count();
		QuizAttempt attempt = attempts.saveAndFlush(
			QuizAttempt.completed(quizSet.getId(), userId, correct, pending.size())
		);
		entityManager.refresh(attempt);
		for (PendingResult pendingResult : pending) {
			results.save(QuizQuestionResult.automatic(
				attempt.getId(), pendingResult.question().getId(), pendingResult.answer(), pendingResult.outcome()
			));
		}
		results.flush();
		submissions.save(QuizAttemptSubmission.of(
			userId, quizSet.getId(), keyHash, fingerprint, attempt.getId()
		));
		return submittedResponse(attempt);
	}

	@Transactional(readOnly = true)
	public QuizAttemptResult result(long userId, String attemptPublicId) {
		QuizAttempt attempt = attempts.findByPublicIdAndUserId(attemptPublicId, userId).orElseThrow(this::notFound);
		QuizSet quizSet = quizSets.findById(attempt.getQuizSetId()).orElseThrow(this::notFound);
		List<QuizQuestion> quizQuestions = questions.findAllByQuizSetIdOrderByNumber(attempt.getQuizSetId());
		Map<Long, QuizQuestionResult> byQuestion = new HashMap<>();
		for (QuizQuestionResult result : results.findAllByAttemptId(attempt.getId())) {
			byQuestion.put(result.getQuestionId(), result);
		}
		List<ShortAnswerQuestionResult> projection = quizQuestions.stream().map(question -> {
			if (question.getType() != QuestionType.SHORT_ANSWER) {
				throw new IllegalStateException("Non-short-answer projection belongs to its dedicated implementation");
			}
			QuizQuestionResult questionResult = byQuestion.get(question.getId());
			if (questionResult == null) {
				throw new IllegalStateException("Attempt result is incomplete");
			}
			return new ShortAnswerQuestionResult(
				question.getPublicId(), question.getNumber(), question.getType(), question.getTopic(), question.getPrompt(),
				questionResult.isAnswered() ? new AnswerValue(questionResult.getSubmittedAnswer()) : null,
				new AnswerValue(question.getRepresentativeAnswer()), questionResult.currentOutcome(),
				questionResult.getGradingRevision(), question.getExplanation(), question.getSourceExcerpt()
			);
		}).toList();
		return new QuizAttemptResult(
			attempt.getPublicId(), quizSet.getPublicId(), attempt.getStatus(), summary(attempt), projection
		);
	}

	@Transactional
	public UpdatedShortAnswerGrading updateShortAnswerGrading(
		long userId,
		String attemptPublicId,
		String questionPublicId,
		String idempotencyKey,
		String requestedOutcome,
		Long expectedRevision
	) {
		byte[] keyHash = QuizRequestDigest.idempotencyKey(idempotencyKey);
		GradingOutcome outcome = parseOutcome(requestedOutcome);
		if (expectedRevision == null || expectedRevision < 0) {
			throw invalid("expectedRevision", "expectedRevision은 0 이상의 정수여야 합니다.");
		}
		QuizAttempt attempt = attempts.findOwnedForUpdate(attemptPublicId, userId).orElseThrow(this::notFound);
		QuizQuestion question = questions.findByPublicIdAndQuizSetId(questionPublicId, attempt.getQuizSetId())
			.orElseThrow(this::notFound);
		QuizQuestionResult questionResult = results.findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
			.orElseThrow(this::notFound);
		byte[] fingerprint = QuizRequestDigest.framed(outcome.name(), Long.toString(expectedRevision));
		var replay = gradingIdempotencies.findByUserIdAndAttemptIdAndQuestionIdAndIdempotencyKeyHash(
			userId, attempt.getId(), question.getId(), keyHash
		);
		if (replay.isPresent()) {
			ShortAnswerGradingIdempotency stored = replay.get();
			if (!Arrays.equals(stored.getRequestFingerprint(), fingerprint)
				|| questionResult.getGradingRevision() != stored.getGradingRevision()
				|| attempt.getSummaryRevision() != stored.getSummaryRevision()) {
				throw conflict();
			}
			return fromStored(questionPublicId, stored);
		}
		if (attempt.getStatus() != QuizAttemptStatus.COMPLETED
			|| question.getType() != QuestionType.SHORT_ANSWER
			|| !questionResult.isAnswered()
			|| questionResult.getGradingRevision() != expectedRevision) {
			throw conflict();
		}

		if (questionResult.currentOutcome() != outcome) {
			int updated = results.updateOverrideIfRevision(
				questionResult.getId(), expectedRevision, outcome, Instant.now(clock)
			);
			if (updated != 1) {
				throw conflict();
			}
			attempt = attempts.findOwnedForUpdate(attemptPublicId, userId).orElseThrow(this::notFound);
			attempt.incrementSummaryRevision();
			attempts.saveAndFlush(attempt);
			questionResult = results.findByAttemptIdAndQuestionId(attempt.getId(), question.getId())
				.orElseThrow(this::notFound);
		}
		ShortAnswerGradingSummary summary = compactSummary(attempt);
		ShortAnswerGradingIdempotency stored = gradingIdempotencies.save(ShortAnswerGradingIdempotency.of(
			userId, attempt.getId(), question.getId(), keyHash, fingerprint,
			questionResult.currentOutcome(), questionResult.getGradingRevision(), summary.revision(),
			summary.scoredGrading().correctQuestionCount(), summary.scoredGrading().gradedQuestionCount(),
			summary.reviewQuestionCount()
		));
		return fromStored(questionPublicId, stored);
	}

	private SubmittedQuizAttempt submittedResponse(QuizAttempt attempt) {
		return new SubmittedQuizAttempt(
			attempt.getPublicId(), attempt.getStatus(),
			new GradingCount(attempt.getAutomaticCorrectCount(), attempt.getAutomaticGradedCount()),
			List.of(), attempt.getCreatedAt()
		);
	}

	private QuizAttemptSummary summary(QuizAttempt attempt) {
		ShortAnswerGradingSummary compact = compactSummary(attempt);
		return new QuizAttemptSummary(
			compact.revision(), compact.scoredGrading(), new EssaySelfAssessmentSummary(0, 0, 0),
			compact.reviewQuestionCount()
		);
	}

	private ShortAnswerGradingSummary compactSummary(QuizAttempt attempt) {
		return new ShortAnswerGradingSummary(
			attempt.getSummaryRevision(),
			new GradingCount(
				Math.toIntExact(results.countCurrentCorrect(attempt.getId())),
				Math.toIntExact(results.countGraded(attempt.getId()))
			),
			Math.toIntExact(results.countReviewRequired(attempt.getId()))
		);
	}

	private UpdatedShortAnswerGrading fromStored(String questionPublicId, ShortAnswerGradingIdempotency stored) {
		return new UpdatedShortAnswerGrading(
			questionPublicId, stored.getOutcome(), stored.getGradingRevision(),
			new ShortAnswerGradingSummary(
				stored.getSummaryRevision(),
				new GradingCount(stored.getCorrectQuestionCount(), stored.getGradedQuestionCount()),
				stored.getReviewQuestionCount()
			)
		);
	}

	private Map<String, QuizResponseRequest> validateAndIndexResponses(
		List<QuizResponseRequest> submittedResponses,
		List<QuizQuestion> quizQuestions
	) {
		Set<String> known = quizQuestions.stream().map(QuizQuestion::getPublicId).collect(java.util.stream.Collectors.toSet());
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

	private byte[] submissionFingerprint(List<QuizResponseRequest> responses) {
		StringBuilder value = new StringBuilder();
		for (QuizResponseRequest response : responses) {
			if (response == null) {
				value.append("<null>");
			} else {
				value.append(response.questionId()).append('\u001f')
					.append(response.selectedChoiceId()).append('\u001f')
					.append(response.blankAnswers()).append('\u001f')
					.append(response.text());
			}
			value.append('\u001e');
		}
		return QuizRequestDigest.framed(value.toString());
	}

	private String writtenText(String text) {
		if (text == null || text.codePoints().allMatch(codePoint ->
			Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint))) {
			return null;
		}
		return text;
	}

	private GradingOutcome parseOutcome(String value) {
		try {
			return GradingOutcome.valueOf(value);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw invalid("outcome", "outcome은 CORRECT 또는 INCORRECT여야 합니다.");
		}
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

	private record PendingResult(QuizQuestion question, String answer, GradingOutcome outcome) {
	}
}
