package com.openmd.server.quiz.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.openmd.server.quiz.dto.request.QuizResponseRequest;
import com.openmd.server.quiz.dto.response.CreatedReviewSnapshot;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.dto.response.SubmittedQuizAttempt;
import com.openmd.server.quiz.dto.response.UpdatedShortAnswerGrading;
import com.openmd.server.quiz.error.QuizErrorCode;
import com.openmd.server.quiz.service.QuizAttemptService;
import com.openmd.server.quiz.service.ReviewSessionSnapshotService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
	@Autowired QuizAttemptService service;
	@Autowired ReviewSessionSnapshotService reviewSnapshots;

	@BeforeEach
	void clearData() {
		for (String table : List.of(
			"quiz_review_session_questions", "quiz_review_sessions",
			"quiz_short_answer_grading_idempotencies", "quiz_attempt_submissions",
			"quiz_question_results", "quiz_attempts", "quiz_short_answer_accepted_answers",
			"quiz_questions", "quiz_sets", "learning_materials", "users"
		)) {
			jdbc.update("DELETE FROM " + table);
		}
	}

	@Test
	void submitsGradesProjectsAndUpdatesAtomicallyWithoutChangingTheAutomaticResult() {
		Fixture fixture = fixture("owner@example.com", List.of("fifo"));
		SubmittedQuizAttempt submitted = service.submit(
			fixture.userId(), fixture.quizSetId(), "submit-1",
			List.of(new QuizResponseRequest(fixture.questionId(), null, null, "  FIFO\u2003"))
		);
		assertEquals(1, submitted.automaticGrading().correctQuestionCount());
		assertEquals(submitted, service.submit(
			fixture.userId(), fixture.quizSetId(), "submit-1",
			List.of(new QuizResponseRequest(fixture.questionId(), null, null, "  FIFO\u2003"))
		));

		QuizAttemptResult initial = service.result(fixture.userId(), submitted.attemptId());
		assertEquals(GradingOutcome.CORRECT, initial.questionResults().getFirst().outcome());
		assertEquals(0, initial.questionResults().getFirst().gradingRevision());

		UpdatedShortAnswerGrading changed = service.updateShortAnswerGrading(
			fixture.userId(), submitted.attemptId(), fixture.questionId(), "grading-1", "INCORRECT", 0L
		);
		assertEquals(1, changed.gradingRevision());
		assertEquals(1, changed.summary().revision());
		assertEquals(0, changed.summary().scoredGrading().correctQuestionCount());
		assertEquals(1, changed.summary().reviewQuestionCount());
		assertEquals(changed, service.updateShortAnswerGrading(
			fixture.userId(), submitted.attemptId(), fixture.questionId(), "grading-1", "INCORRECT", 0L
		));

		var attempt = attempts.findByPublicIdAndUserId(submitted.attemptId(), fixture.userId()).orElseThrow();
		var stored = results.findAllByAttemptId(attempt.getId()).getFirst();
		assertEquals("  FIFO\u2003", stored.getSubmittedAnswer());
		assertEquals(GradingOutcome.CORRECT, stored.getAutomaticOutcome());
		assertEquals(GradingOutcome.INCORRECT, stored.getUserOverrideOutcome());

		UpdatedShortAnswerGrading noOp = service.updateShortAnswerGrading(
			fixture.userId(), submitted.attemptId(), fixture.questionId(), "grading-noop", "INCORRECT", 1L
		);
		assertEquals(1, noOp.gradingRevision());
		assertEquals(1, noOp.summary().revision());
		BusinessException stale = assertThrows(BusinessException.class, () -> service.updateShortAnswerGrading(
			fixture.userId(), submitted.attemptId(), fixture.questionId(), "grading-stale", "CORRECT", 0L
		));
		assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, stale.getErrorCode());
	}

	@Test
	void keepsAnActiveReviewSnapshotAndRejectsUnansweredOrForeignShortAnswers() {
		Fixture fixture = fixture("first@example.com", List.of("fifo"));
		SubmittedQuizAttempt submitted = service.submit(
			fixture.userId(), fixture.quizSetId(), "submit-empty", List.of()
		);
		QuizAttemptResult unanswered = service.result(fixture.userId(), submitted.attemptId());
		assertNull(unanswered.questionResults().getFirst().response());
		BusinessException cannotOverride = assertThrows(BusinessException.class, () -> service.updateShortAnswerGrading(
			fixture.userId(), submitted.attemptId(), fixture.questionId(), "grading-empty", "CORRECT", 0L
		));
		assertEquals(QuizErrorCode.ATTEMPT_CONFLICT, cannotOverride.getErrorCode());

		CreatedReviewSnapshot snapshot = reviewSnapshots.createForAttempt(fixture.userId(), submitted.attemptId());
		assertEquals(1, snapshot.questionCount());
		long snapshotItems = jdbc.queryForObject(
			"SELECT COUNT(*) FROM quiz_review_session_questions", Long.class
		);
		assertEquals(1L, snapshotItems);

		Fixture answeredFixture = fixture("second@example.com", List.of("fifo"));
		SubmittedQuizAttempt answered = service.submit(
			answeredFixture.userId(), answeredFixture.quizSetId(), "submit-wrong",
			List.of(new QuizResponseRequest(answeredFixture.questionId(), null, null, "선입선출"))
		);
		CreatedReviewSnapshot active = reviewSnapshots.createForAttempt(answeredFixture.userId(), answered.attemptId());
		service.updateShortAnswerGrading(
			answeredFixture.userId(), answered.attemptId(), answeredFixture.questionId(),
			"grading-correct", "CORRECT", 0L
		);
		assertEquals(1L, jdbc.queryForObject(
			"SELECT COUNT(*) FROM quiz_review_session_questions q JOIN quiz_review_sessions s ON s.id=q.review_session_id WHERE s.public_id=?",
			Long.class, active.reviewSessionId()
		));

		BusinessException foreign = assertThrows(BusinessException.class, () -> service.result(
			fixture.userId(), answered.attemptId()
		));
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, foreign.getErrorCode());
	}

	private Fixture fixture(String email, List<String> acceptedAnswers) {
		User user = User.pending(email, email, "hash");
		user.activate(Instant.parse("2026-08-20T00:00:00Z"));
		user = users.saveAndFlush(user);
		LearningMaterial material = materials.saveAndFlush(LearningMaterial.create(
			user.getId(), "자료", "FIFO 자료", SourceType.PASTE, digest(1), digest(2)
		));
		QuizSet set = quizSets.saveAndFlush(QuizSet.ready(user.getId(), material.getId()));
		QuizQuestion question = questions.saveAndFlush(QuizQuestion.shortAnswer(
			set.getId(), 1, "큐", "처리 순서는?", "fifo", acceptedAnswers, "FIFO 구조", "FIFO 원칙"
		));
		return new Fixture(user.getId(), set.getPublicId(), question.getPublicId());
	}

	private byte[] digest(int firstByte) {
		byte[] value = new byte[32];
		value[0] = (byte) firstByte;
		return value;
	}

	private record Fixture(long userId, String quizSetId, String questionId) {
	}
}
