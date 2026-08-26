package com.openmd.server.learningmaterial.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.dto.command.CreateLearningMaterialCommand;
import com.openmd.server.learningmaterial.dto.response.CreatedLearningMaterial;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialPage;
import com.openmd.server.learningmaterial.service.LearningMaterialService;
import com.openmd.server.learningmaterial.service.LearningMaterialQueryService;
import com.openmd.server.learningmaterial.error.LearningMaterialErrorCode;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.dao.DataAccessException;
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
class LearningMaterialMySqlIntegrationTest {

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

	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired UserRepository users;
	@Autowired LearningMaterialService service;
	@Autowired LearningMaterialQueryService queries;
	@Autowired QuizSetRepository quizSets;

	@BeforeEach
	void clearMaterials() {
		jdbcTemplate.update("DELETE FROM quiz_sets");
		jdbcTemplate.update("DELETE FROM learning_materials");
		jdbcTemplate.update("DELETE FROM users");
	}

	@Test
	void appliesV2AndPersistsTwentyThousandUtf8mb4CodePointsWithConstraints() {
		Integer migrationSucceeded = jdbcTemplate.queryForObject(
			"SELECT success FROM flyway_schema_history WHERE version = '2'", Integer.class
		);
		assertEquals(1, migrationSucceeded);
		long userId = activeUser("one@example.com").getId();
		String content = "😀".repeat(20_000);

		CreatedLearningMaterial created = service.create(
			userId, "large-content", new CreateLearningMaterialCommand(" 제목 ", content, "NOTION")
		);

		assertNotNull(created.materialId());
		assertEquals(20_000, created.contentLength());
		assertEquals(content, jdbcTemplate.queryForObject(
			"SELECT content FROM learning_materials WHERE id = ?", String.class, Long.valueOf(created.materialId())
		));
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
			INSERT INTO learning_materials (
			  user_id, title, content, source_type,
			  idempotency_key_hash, request_fingerprint, created_at, updated_at
			) VALUES (?, '제목', ?, 'PASTE', ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			""", userId, "😀".repeat(20_001), new byte[32], new byte[32]));
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
			INSERT INTO learning_materials (
			  user_id, title, content, source_type,
			  idempotency_key_hash, request_fingerprint, created_at, updated_at
			) VALUES (?, '제목', '본문', 'UNKNOWN', ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			""", userId, digestFixture(1), digestFixture(2)));
		assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
			INSERT INTO learning_materials (
			  user_id, title, content, source_type,
			  idempotency_key_hash, request_fingerprint, created_at, updated_at
			) VALUES (999999, '제목', '본문', 'PASTE', ?, ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			""", digestFixture(3), digestFixture(4)));

		BusinessException tooLong = assertThrows(BusinessException.class, () -> service.create(
			userId, "too-long", new CreateLearningMaterialCommand("제목", "😀".repeat(20_001), "PASTE")
		));
		assertEquals(LearningMaterialErrorCode.CONTENT_TOO_LONG, tooLong.getErrorCode());
	}

	@Test
	void doesNotStoreTheCalculatedContentEditStatusOnLearningMaterials() {
		Integer storedStatusColumns = jdbcTemplate.queryForObject("""
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = DATABASE()
			  AND table_name = 'learning_materials'
			  AND column_name = 'content_edit_status'
			""", Integer.class);

		assertEquals(0, storedStatusColumns);
	}

	@Test
	void pagesOwnedTitleMatchesInStableOrderAndCalculatesGenerationLocks() {
		long ownerId = activeUser("owner@example.com").getId();
		long otherId = activeUser("other@example.com").getId();
		CreatedLearningMaterial older = service.create(ownerId, "older", new CreateLearningMaterialCommand(
			"운영체제 오래된 자료", "본문😀", "PASTE"));
		CreatedLearningMaterial newer = service.create(ownerId, "newer", new CreateLearningMaterialCommand(
			"운영체제 최신 자료", "최신", "NOTION"));
		service.create(ownerId, "unmatched", new CreateLearningMaterialCommand("네트워크", "본문", "PASTE"));
		service.create(otherId, "other-owner", new CreateLearningMaterialCommand(
			"운영체제 타인 자료", "본문", "PASTE"));
		jdbcTemplate.update(
			"UPDATE learning_materials SET updated_at = ? WHERE id = ?",
			java.sql.Timestamp.from(Instant.parse("2026-08-25T01:00:00Z")), Long.valueOf(older.materialId()));
		jdbcTemplate.update(
			"UPDATE learning_materials SET updated_at = ? WHERE id = ?",
			java.sql.Timestamp.from(Instant.parse("2026-08-26T01:00:00Z")), Long.valueOf(newer.materialId()));
		quizSets.saveAndFlush(QuizSet.generating(ownerId, Long.parseLong(older.materialId())));

		LearningMaterialPage firstPage = queries.list(ownerId, 1, 1, "\u2003운영체제\u00a0");
		LearningMaterialPage secondPage = queries.list(ownerId, 2, 1, "운영체제");
		LearningMaterialPage outside = queries.list(ownerId, 3, 1, "운영체제");

		assertEquals(2, firstPage.totalElements());
		assertEquals(2, firstPage.totalPages());
		assertEquals(newer.materialId(), firstPage.items().getFirst().materialId());
		assertEquals(ContentEditStatus.EDITABLE, firstPage.items().getFirst().contentEditStatus());
		assertEquals(older.materialId(), secondPage.items().getFirst().materialId());
		assertEquals(ContentEditStatus.LOCKED_GENERATING, secondPage.items().getFirst().contentEditStatus());
		assertEquals(2, outside.totalElements());
		assertEquals(2, outside.totalPages());
		assertEquals(0, outside.items().size());
		assertEquals(3, queries.detail(ownerId, Long.parseLong(older.materialId())).contentLength());

		BusinessException hidden = assertThrows(
			BusinessException.class,
			() -> queries.detail(ownerId, Long.parseLong(service.create(
				otherId,
				"hidden-detail",
				new CreateLearningMaterialCommand("타인 상세", "본문", "PASTE")
			).materialId()))
		);
		assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, hidden.getErrorCode());
	}

	@Test
	void scopesKeysByUserAndConvergesConcurrentSameKeyRequestsToOneRow() throws Exception {
		long firstUser = activeUser("first@example.com").getId();
		long secondUser = activeUser("second@example.com").getId();
		CreateLearningMaterialCommand command = new CreateLearningMaterialCommand("제목", "본문", "PASTE");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<CreatedLearningMaterial> first = executor.submit(() -> createAfterSignal(firstUser, command, ready, start));
			Future<CreatedLearningMaterial> second = executor.submit(() -> createAfterSignal(firstUser, command, ready, start));
			org.junit.jupiter.api.Assertions.assertTrue(ready.await(5, TimeUnit.SECONDS));
			start.countDown();

			CreatedLearningMaterial firstResult = first.get(10, TimeUnit.SECONDS);
			CreatedLearningMaterial secondResult = second.get(10, TimeUnit.SECONDS);
			assertEquals(firstResult, secondResult);
			assertEquals(1L, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM learning_materials WHERE user_id = ?", Long.class, firstUser
			));
			BusinessException conflictingPayload = assertThrows(BusinessException.class, () -> service.create(
				firstUser,
				"concurrent-key",
				new CreateLearningMaterialCommand("제목", "다른 본문", "PASTE")
			));
			assertEquals(CommonErrorCode.INVALID_INPUT, conflictingPayload.getErrorCode());
			assertEquals(1L, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM learning_materials WHERE user_id = ?", Long.class, firstUser
			));

			CreatedLearningMaterial otherOwner = service.create(secondUser, "concurrent-key", command);
			assertNotNull(otherOwner.materialId());
			assertEquals(2L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM learning_materials", Long.class));
		} finally {
			executor.shutdownNow();
		}
	}

	private CreatedLearningMaterial createAfterSignal(
		long userId,
		CreateLearningMaterialCommand command,
		CountDownLatch ready,
		CountDownLatch start
	) throws InterruptedException {
		ready.countDown();
		start.await();
		return service.create(userId, "concurrent-key", command);
	}

	private User activeUser(String email) {
		User user = User.pending(email, email, "hash");
		user.activate(Instant.parse("2026-08-20T00:00:00Z"));
		return users.saveAndFlush(user);
	}

	private byte[] digestFixture(int firstByte) {
		byte[] value = new byte[32];
		value[0] = (byte) firstByte;
		return value;
	}
}
