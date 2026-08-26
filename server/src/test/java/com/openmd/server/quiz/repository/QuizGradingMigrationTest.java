package com.openmd.server.quiz.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class QuizGradingMigrationTest {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
		.withDatabaseName("openmd_quiz_migration")
		.withUsername("openmd")
		.withPassword("openmd")
		.withStartupTimeout(Duration.ofMinutes(2));

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrate() {
		Flyway.configure()
			.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
			.load()
			.migrate();
		jdbc = new JdbcTemplate(new DriverManagerDataSource(
			MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()
		));

		jdbc.update("""
			INSERT INTO users (
				id, email, normalized_email, password_hash, email_verified_at, status,
				activated_at, suspended_at, withdrawn_at, created_at, updated_at
			) VALUES (1, 'quiz-migration@example.com', 'quiz-migration@example.com', 'hash',
				NOW(6), 'ACTIVE', NOW(6), NULL, NULL, NOW(6), NOW(6))
			""");
		jdbc.update("""
			INSERT INTO learning_materials (
				id, user_id, title, content, source_type,
				idempotency_key_hash, request_fingerprint, created_at, updated_at
			) VALUES (1, 1, '자료', '내용', 'PASTE',
				UNHEX(REPEAT('01', 32)), UNHEX(REPEAT('02', 32)), NOW(6), NOW(6))
			""");
	}

	@Test
	void createsTypeSpecificAnswerTablesAndRemovesTheGenericAnswerTable() {
		List<String> expectedTables = List.of(
			"quiz_question_choices",
			"quiz_short_answer_answers",
			"quiz_essay_answer_guides",
			"quiz_fill_in_the_blanks",
			"quiz_fill_in_the_blank_answers"
		);

		Integer expectedTableCount = jdbc.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = DATABASE()
			  AND table_name IN (?, ?, ?, ?, ?)
			""", Integer.class, expectedTables.toArray());
		Integer genericTableCount = jdbc.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = DATABASE()
			  AND table_name = 'quiz_question_answers'
			""", Integer.class);

		assertEquals(expectedTables.size(), expectedTableCount);
		assertEquals(0, genericTableCount);
		assertColumns("quiz_question_choices", "id", "public_id", "question_id", "choice_value", "is_correct");
		assertColumns("quiz_short_answer_answers", "id", "question_id", "answer_value", "normalized_value");
		assertColumns("quiz_essay_answer_guides", "question_id", "model_answer", "key_points");
		assertColumns("quiz_fill_in_the_blanks", "id", "public_id", "question_id", "blank_number");
		assertColumns("quiz_fill_in_the_blank_answers", "id", "blank_id", "answer_value", "normalized_value");
	}

	@Test
	void quizSetFailureCodeMustMatchTheSetStatus() {
		insertQuizSet(10, "00000000-0000-0000-0000-000000000010", "GENERATING", null);
		insertQuizSet(11, "00000000-0000-0000-0000-000000000011", "READY", null);
		insertQuizSet(12, "00000000-0000-0000-0000-000000000012", "FAILED", "SOURCE_INSUFFICIENT");
		insertQuizSet(13, "00000000-0000-0000-0000-000000000013", "FAILED", "GENERATION_FAILED");

		assertThrows(DataAccessException.class, () ->
			insertQuizSet(14, "00000000-0000-0000-0000-000000000014", "FAILED", null));
		assertThrows(DataAccessException.class, () ->
			insertQuizSet(15, "00000000-0000-0000-0000-000000000015", "READY", "GENERATION_FAILED"));
		assertThrows(DataAccessException.class, () ->
			insertQuizSet(16, "00000000-0000-0000-0000-000000000016", "FAILED", "UNKNOWN"));
	}

	@Test
	void submittedAnswersAllowOnlyTheThreeDocumentedShapesAndRejectDuplicateBlankAnswers() {
		insertQuizSet(20, "00000000-0000-0000-0000-000000000020", "READY", null);
		insertQuestion(20, 20, "00000000-0000-0000-0000-000000000120", "MULTIPLE_CHOICE");
		insertQuestion(21, 20, "00000000-0000-0000-0000-000000000121", "FILL_IN_THE_BLANK");
		jdbc.update("""
			INSERT INTO quiz_question_choices
				(id, public_id, question_id, choice_value, is_correct, created_at, updated_at)
			VALUES (20, '00000000-0000-0000-0000-000000000220', 20, '보기', TRUE, NOW(6), NOW(6))
			""");
		jdbc.update("""
			INSERT INTO quiz_fill_in_the_blanks
				(id, public_id, question_id, blank_number, created_at, updated_at)
			VALUES (20, '00000000-0000-0000-0000-000000000320', 21, 1, NOW(6), NOW(6))
			""");
		jdbc.update("""
			INSERT INTO quiz_attempts
				(id, public_id, quiz_set_id, user_id, attempt_type, source_attempt_id, status,
				 submitted_at, completed_at, created_at, updated_at)
			VALUES (20, '00000000-0000-0000-0000-000000000420', 20, 1, 'MAIN', NULL,
				'COMPLETED', NOW(6), NOW(6), NOW(6), NOW(6))
			""");
		insertAttemptQuestion(20, 20, 20, 1);
		insertAttemptQuestion(21, 20, 21, 2);

		jdbc.update("""
			INSERT INTO quiz_submitted_answers
				(id, attempt_question_id, selected_choice_id, blank_id, answer_value, created_at, updated_at)
			VALUES (20, 20, 20, NULL, NULL, NOW(6), NOW(6))
			""");
		jdbc.update("""
			INSERT INTO quiz_submitted_answers
				(id, attempt_question_id, selected_choice_id, blank_id, answer_value, created_at, updated_at)
			VALUES (21, 21, NULL, 20, 'FIFO', NOW(6), NOW(6))
			""");
		jdbc.update("""
			INSERT INTO quiz_submitted_answers
				(id, attempt_question_id, selected_choice_id, blank_id, answer_value, created_at, updated_at)
			VALUES (22, 20, NULL, NULL, '서술 또는 단답', NOW(6), NOW(6))
			""");

		assertThrows(DataAccessException.class, () -> jdbc.update("""
			INSERT INTO quiz_submitted_answers
				(id, attempt_question_id, selected_choice_id, blank_id, answer_value, created_at, updated_at)
			VALUES (23, 20, 20, NULL, '중복 모양', NOW(6), NOW(6))
			"""));
		assertThrows(DataAccessException.class, () -> jdbc.update("""
			INSERT INTO quiz_submitted_answers
				(id, attempt_question_id, selected_choice_id, blank_id, answer_value, created_at, updated_at)
			VALUES (24, 20, NULL, NULL, NULL, NOW(6), NOW(6))
			"""));
		assertThrows(DataAccessException.class, () -> jdbc.update("""
			INSERT INTO quiz_submitted_answers
				(id, attempt_question_id, selected_choice_id, blank_id, answer_value, created_at, updated_at)
			VALUES (25, 21, NULL, 20, '두 번째 답', NOW(6), NOW(6))
			"""));
	}

	private static void assertColumns(String tableName, String... expectedColumns) {
		List<String> columns = jdbc.queryForList("""
			SELECT column_name
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = ?
			""", String.class, tableName);
		for (String expectedColumn : expectedColumns) {
			assertEquals(true, columns.contains(expectedColumn), tableName + "." + expectedColumn);
		}
	}

	private static void insertQuizSet(long id, String publicId, String status, String failureCode) {
		jdbc.update("""
			INSERT INTO quiz_sets
				(id, public_id, user_id, learning_material_id, status, failure_code, created_at, updated_at)
			VALUES (?, ?, 1, 1, ?, ?, NOW(6), NOW(6))
			""", id, publicId, status, failureCode);
	}

	private static void insertQuestion(long id, long quizSetId, String publicId, String type) {
		jdbc.update("""
			INSERT INTO quiz_questions
				(id, public_id, quiz_set_id, question_number, question_type, topic, prompt,
				 explanation, source_excerpt, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, '주제', '문제', '해설', '근거', NOW(6), NOW(6))
			""", id, publicId, quizSetId, id - 19, type);
	}

	private static void insertAttemptQuestion(long id, long attemptId, long questionId, int sequence) {
		jdbc.update("""
			INSERT INTO quiz_attempt_questions
				(id, attempt_id, question_id, source_attempt_question_id, sequence_number,
				 automatic_grading_result, final_grading_result, grading_method,
				 review_resolved_at, created_at, updated_at)
			VALUES (?, ?, ?, NULL, ?, NULL, NULL, NULL, NULL, NOW(6), NOW(6))
			""", id, attemptId, questionId, sequence);
	}
}
