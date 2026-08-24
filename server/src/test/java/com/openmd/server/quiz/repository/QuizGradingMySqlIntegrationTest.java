package com.openmd.server.quiz.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.quiz.domain.entity.QuizQuestion;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import com.openmd.server.quiz.dto.model.QuizAttemptSubmissionResult;
import com.openmd.server.quiz.dto.request.QuizResponseRequest;
import com.openmd.server.quiz.dto.response.CreatedReviewSnapshot;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.dto.response.SubmittedQuizAttempt;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.service.QuizAttemptResultService;
import com.openmd.server.quiz.service.QuizAttemptSubmissionService;
import com.openmd.server.quiz.service.ReviewSessionSnapshotService;
import com.openmd.server.quiz.service.ShortAnswerGradingService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
@SpringBootTest(properties = {
	"openmd.auth.enabled=false",
	"spring.jpa.open-in-view=false",
	"spring.autoconfigure.exclude="
		+ "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
		+ "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
		+ "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
})
class QuizGradingMySqlIntegrationTest {
	private static final String FAIL_RESULT_INSERT_CHECK = "test_fail_quiz_question_result_insert";
	private static final String FAIL_RESULT_INSERT_ANSWER = "__force_result_insert_failure__";

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
		.withDatabaseName("openmd")
		.withUsername("openmd")
		.withPassword("openmd")
		.withStartupTimeout(Duration.ofMinutes(2));

	@DynamicPropertySource
	static void mysqlProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
	}

	@Autowired JdbcTemplate jdbc;
	@Autowired UserRepository users;
	@Autowired LearningMaterialRepository materials;
	@Autowired QuizSetRepository quizSets;
	@Autowired QuizQuestionRepository questions;
	@Autowired QuizAttemptRepository attempts;
	@Autowired QuizQuestionResultRepository results;
	@Autowired QuizAttemptSubmissionService submissions;
	@Autowired QuizAttemptResultService attemptResults;
	@Autowired ShortAnswerGradingService gradings;
	@Autowired ReviewSessionSnapshotService reviewSnapshots;

	@BeforeEach
	void clearData() {
		dropResultInsertFailureCheck();
		for (String table : List.of(
			"quiz_review_session_questions", "quiz_review_sessions",
			"quiz_question_results", "quiz_attempts", "quiz_short_answer_accepted_answers",
			"quiz_questions", "quiz_sets", "learning_materials", "users"
		)) {
			jdbc.update("DELETE FROM " + table);
		}
	}

	@Test
	void concurrentSubmissionsWithTheSameAttemptIdCreateExactlyOneAttempt() throws Exception {
		Fixture fixture = fixture("concurrent-submit@example.com", List.of("fifo"));
		String attemptId = "550e8400-e29b-41d4-a716-446655440010";
		List<QuizResponseRequest> responses = List.of(
			new QuizResponseRequest(fixture.questionId(), null, null, "FIFO")
		);

		List<QuizAttemptSubmissionResult> submitted = runConcurrently(
			() -> submissions.submit(fixture.userId(), fixture.quizSetId(), attemptId, responses),
			() -> submissions.submit(fixture.userId(), fixture.quizSetId(), attemptId, responses)
		);

		assertTrue(submitted.getFirst().created() ^ submitted.getLast().created());
		assertEquals(attemptId, submitted.getFirst().attempt().attemptId());
		assertEquals(attemptId, submitted.getLast().attempt().attemptId());
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts", Long.class));
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_question_results", Long.class));
	}

	@Test
	void concurrentOppositeOverridesBothSucceedAndLeaveAConsistentResultSummary() throws Exception {
		Fixture fixture = fixture("concurrent-grading@example.com", List.of("fifo"));
		SubmittedQuizAttempt submitted = submissions.submit(
			fixture.userId(), fixture.quizSetId(), "550e8400-e29b-41d4-a716-446655440011",
			List.of(new QuizResponseRequest(fixture.questionId(), null, null, "선입선출"))
		).attempt();

		List<QuizAttemptResult> updates = runConcurrently(
			() -> gradings.update(fixture.userId(), submitted.attemptId(), fixture.questionId(), "CORRECT"),
			() -> gradings.update(fixture.userId(), submitted.attemptId(), fixture.questionId(), "INCORRECT")
		);
		assertEquals(2, updates.size());

		var attempt = attempts.findByPublicIdAndUserId(submitted.attemptId(), fixture.userId()).orElseThrow();
		GradingOutcome storedOutcome = results.findByAttemptIdAndQuestionId(
			attempt.getId(), questions.findByPublicIdAndQuizSetId(fixture.questionId(), attempt.getQuizSetId()).orElseThrow().getId()
		).orElseThrow().currentOutcome();
		QuizAttemptResult current = attemptResults.result(fixture.userId(), submitted.attemptId());

		assertEquals(storedOutcome, current.questionResults().getFirst().outcome());
		assertEquals(
			storedOutcome == GradingOutcome.CORRECT ? 1 : 0,
			current.summary().scoredGrading().correctQuestionCount()
		);
		assertEquals(
			storedOutcome == GradingOutcome.INCORRECT ? 1 : 0,
			current.summary().reviewQuestionCount()
		);
	}

	@Test
	void rollsBackTheAttemptWhenAQuestionResultInsertFails() {
		Fixture fixture = fixture("rollback-submit@example.com", List.of("fifo"));
		jdbc.execute("ALTER TABLE quiz_question_results ADD CONSTRAINT " + FAIL_RESULT_INSERT_CHECK
			+ " CHECK (submitted_answer <> '" + FAIL_RESULT_INSERT_ANSWER + "')");

		try {
			assertThrows(DataIntegrityViolationException.class, () -> submissions.submit(
				fixture.userId(), fixture.quizSetId(), "550e8400-e29b-41d4-a716-446655440012",
				List.of(new QuizResponseRequest(fixture.questionId(), null, null, FAIL_RESULT_INSERT_ANSWER))
			));
		} finally {
			dropResultInsertFailureCheck();
		}

		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts", Long.class));
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_question_results", Long.class));
	}

	@Test
	void migrationDoesNotCreateReplayTablesOrPublicRevisionColumns() {
		Integer replayTableCount = jdbc.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = DATABASE()
			  AND table_name IN ('quiz_attempt_submissions', 'quiz_short_answer_grading_idempotencies')
			""", Integer.class);
		Integer revisionColumnCount = jdbc.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND (
				(table_name = 'quiz_attempts' AND column_name = 'summary_revision')
				OR (table_name = 'quiz_question_results' AND column_name IN ('grading_revision', 'corrected_at'))
				OR (table_name = 'quiz_review_sessions' AND column_name = 'source_summary_revision')
			  )
			""", Integer.class);

		assertEquals(0, replayTableCount);
		assertEquals(0, revisionColumnCount);
	}

	@Test
	void submitsGradesProjectsAndUpdatesAtomicallyWithoutChangingTheAutomaticResult() {
		Fixture fixture = fixture("owner@example.com", List.of("fifo"));
		String attemptId = "550e8400-e29b-41d4-a716-446655440000";
		QuizAttemptSubmissionResult firstSubmission = submissions.submit(
			fixture.userId(), fixture.quizSetId(), attemptId,
			List.of(new QuizResponseRequest(fixture.questionId(), null, null, "  FIFO\u2003"))
		);
		SubmittedQuizAttempt submitted = firstSubmission.attempt();
		assertEquals(true, firstSubmission.created());
		assertEquals(attemptId, submitted.attemptId());
		assertEquals(1, submitted.automaticGrading().correctQuestionCount());

		QuizAttemptSubmissionResult replayWithDifferentBody = submissions.submit(
			fixture.userId(), fixture.quizSetId(), attemptId,
			List.of(new QuizResponseRequest(fixture.questionId(), null, null, "선입선출"))
		);
		assertEquals(false, replayWithDifferentBody.created());
		assertEquals(submitted, replayWithDifferentBody.attempt());
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts", Long.class));

		QuizAttemptResult initial = attemptResults.result(fixture.userId(), submitted.attemptId());
		assertEquals(GradingOutcome.CORRECT, initial.questionResults().getFirst().outcome());

		QuizAttemptResult changed = gradings.update(
			fixture.userId(), submitted.attemptId(), fixture.questionId(), "INCORRECT"
		);
		assertEquals(0, changed.summary().scoredGrading().correctQuestionCount());
		assertEquals(1, changed.summary().reviewQuestionCount());
		assertEquals(changed, attemptResults.result(fixture.userId(), submitted.attemptId()));
		assertEquals(changed, gradings.update(
			fixture.userId(), submitted.attemptId(), fixture.questionId(), "INCORRECT"
		));

		var attempt = attempts.findByPublicIdAndUserId(submitted.attemptId(), fixture.userId()).orElseThrow();
		var stored = results.findAllByAttemptId(attempt.getId()).getFirst();
		assertEquals("  FIFO\u2003", stored.getSubmittedAnswer());
		assertEquals(GradingOutcome.CORRECT, stored.getAutomaticOutcome());
		assertEquals(GradingOutcome.INCORRECT, stored.getUserOverrideOutcome());

		QuizAttemptResult lastWrite = gradings.update(
			fixture.userId(), submitted.attemptId(), fixture.questionId(), "CORRECT"
		);
		assertEquals(GradingOutcome.CORRECT, lastWrite.questionResults().getFirst().outcome());
		assertEquals(1, lastWrite.summary().scoredGrading().correctQuestionCount());
	}

	@Test
	void keepsAnActiveReviewSnapshotAndRejectsUnansweredOrForeignShortAnswers() {
		Fixture fixture = fixture("first@example.com", List.of("fifo"));
		SubmittedQuizAttempt submitted = submissions.submit(
			fixture.userId(), fixture.quizSetId(), "550e8400-e29b-41d4-a716-446655440001", List.of()
		).attempt();
		QuizAttemptResult unanswered = attemptResults.result(fixture.userId(), submitted.attemptId());
		assertNull(unanswered.questionResults().getFirst().response());
		BusinessException cannotOverride = assertThrows(BusinessException.class, () -> gradings.update(
			fixture.userId(), submitted.attemptId(), fixture.questionId(), "CORRECT"
		));
		assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, cannotOverride.getErrorCode());

		CreatedReviewSnapshot snapshot = reviewSnapshots.createForAttempt(fixture.userId(), submitted.attemptId());
		assertEquals(1, snapshot.questionCount());
		long snapshotItems = jdbc.queryForObject(
			"SELECT COUNT(*) FROM quiz_review_session_questions", Long.class
		);
		assertEquals(1L, snapshotItems);

		Fixture answeredFixture = fixture("second@example.com", List.of("fifo"));
		SubmittedQuizAttempt answered = submissions.submit(
			answeredFixture.userId(), answeredFixture.quizSetId(), "550e8400-e29b-41d4-a716-446655440002",
			List.of(new QuizResponseRequest(answeredFixture.questionId(), null, null, "선입선출"))
		).attempt();
		CreatedReviewSnapshot active = reviewSnapshots.createForAttempt(answeredFixture.userId(), answered.attemptId());
		gradings.update(
			answeredFixture.userId(), answered.attemptId(), answeredFixture.questionId(),
			"CORRECT"
		);
		assertEquals(1L, jdbc.queryForObject(
			"SELECT COUNT(*) FROM quiz_review_session_questions q JOIN quiz_review_sessions s ON s.id=q.review_session_id WHERE s.public_id=?",
			Long.class, active.reviewSessionId()
		));

		BusinessException foreign = assertThrows(BusinessException.class, () -> attemptResults.result(
			fixture.userId(), answered.attemptId()
		));
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, foreign.getErrorCode());
	}

	@Test
	void rejectsMalformedOrReusedAttemptIdsWithoutCreatingAnotherAttempt() {
		Fixture first = fixture("uuid-first@example.com", List.of("fifo"));
		Fixture second = fixture("uuid-second@example.com", List.of("fifo"));

		BusinessException malformed = assertThrows(BusinessException.class, () -> submissions.submit(
			first.userId(), first.quizSetId(), "not-a-uuid", List.of()
		));
		assertEquals(CommonErrorCode.INVALID_INPUT, malformed.getErrorCode());
		assertEquals("attemptId", malformed.getFields().getFirst().field());

		String attemptId = "550e8400-e29b-41d4-a716-446655440003";
		submissions.submit(first.userId(), first.quizSetId(), attemptId, List.of());
		BusinessException reused = assertThrows(BusinessException.class, () -> submissions.submit(
			second.userId(), second.quizSetId(), attemptId, List.of()
		));
		assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, reused.getErrorCode());
		assertEquals("attemptId", reused.getFields().getFirst().field());
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts", Long.class));
	}

	@Test
	void rejectsSubmissionsToANonReadyOwnedSetOrAnotherUsersSet() {
		Fixture owner = fixture("set-state-owner@example.com", List.of("fifo"));
		Fixture other = fixture("set-state-other@example.com", List.of("fifo"));
		jdbc.update("UPDATE quiz_sets SET status = 'GENERATING' WHERE public_id = ?", owner.quizSetId());

		BusinessException nonReady = assertThrows(BusinessException.class, () -> submissions.submit(
			owner.userId(), owner.quizSetId(), "550e8400-e29b-41d4-a716-446655440013", List.of()
		));
		assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, nonReady.getErrorCode());

		BusinessException foreign = assertThrows(BusinessException.class, () -> submissions.submit(
			owner.userId(), other.quizSetId(), "550e8400-e29b-41d4-a716-446655440014", List.of()
		));
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, foreign.getErrorCode());
		assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts", Long.class));
	}

	@Test
	void rejectsAnAttemptIdAlreadyUsedByTheSameUserInAnotherQuizSet() {
		Fixture first = fixture("same-user-uuid@example.com", List.of("fifo"));
		Fixture second = fixture(first.userId(), 3, List.of("fifo"));
		String attemptId = "550e8400-e29b-41d4-a716-446655440015";
		submissions.submit(first.userId(), first.quizSetId(), attemptId, List.of());

		BusinessException reused = assertThrows(BusinessException.class, () -> submissions.submit(
			first.userId(), second.quizSetId(), attemptId, List.of()
		));

		assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, reused.getErrorCode());
		assertEquals("attemptId", reused.getFields().getFirst().field());
		assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM quiz_attempts", Long.class));
	}

	private Fixture fixture(String email, List<String> acceptedAnswers) {
		User user = User.pending(email, email, "hash");
		user.activate(Instant.parse("2026-08-20T00:00:00Z"));
		user = users.saveAndFlush(user);
		return fixture(user.getId(), 1, acceptedAnswers);
	}

	private Fixture fixture(long userId, int digestSeed, List<String> acceptedAnswers) {
		LearningMaterial material = materials.saveAndFlush(LearningMaterial.create(
			userId, "자료", "FIFO 자료", SourceType.PASTE, digest(digestSeed), digest(digestSeed + 1)
		));
		QuizSet set = quizSets.saveAndFlush(QuizSet.ready(userId, material.getId()));
		QuizQuestion question = questions.saveAndFlush(QuizQuestion.shortAnswer(
			set.getId(), 1, "큐", "처리 순서는?", "fifo", acceptedAnswers, "FIFO 구조", "FIFO 원칙"
		));
		return new Fixture(userId, set.getPublicId(), question.getPublicId());
	}

	private byte[] digest(int firstByte) {
		byte[] value = new byte[32];
		value[0] = (byte) firstByte;
		return value;
	}

	private void dropResultInsertFailureCheck() {
		Integer constraintCount = jdbc.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.table_constraints
			WHERE table_schema = DATABASE()
			  AND table_name = 'quiz_question_results'
			  AND constraint_name = ?
			""", Integer.class, FAIL_RESULT_INSERT_CHECK);
		if (constraintCount != null && constraintCount > 0) {
			jdbc.execute("ALTER TABLE quiz_question_results DROP CHECK " + FAIL_RESULT_INSERT_CHECK);
		}
	}

	private <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<T> firstResult = executor.submit(awaitStart(first, ready, start));
			Future<T> secondResult = executor.submit(awaitStart(second, ready, start));
			assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();
			return List.of(
				firstResult.get(15, TimeUnit.SECONDS),
				secondResult.get(15, TimeUnit.SECONDS)
			);
		} finally {
			start.countDown();
			executor.shutdownNow();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	private <T> Callable<T> awaitStart(Callable<T> task, CountDownLatch ready, CountDownLatch start) {
		return () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent test start timed out");
			}
			return task.call();
		};
	}

	private record Fixture(long userId, String quizSetId, String questionId) {
	}
}
