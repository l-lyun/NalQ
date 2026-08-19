package com.openmd.server.auth.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.auth.application.EmailVerificationStore;
import com.openmd.server.auth.application.IssuedRefreshToken;
import com.openmd.server.auth.application.RefreshSessionStore;
import com.openmd.server.auth.application.RefreshTokenService;
import com.openmd.server.auth.domain.AuthErrorCode;
import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserRepository;
import com.openmd.server.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

@Testcontainers
@Tag("integration")
@SpringBootTest(properties = {
	"openmd.auth.enabled=false",
	"spring.jpa.open-in-view=false"
})
class AuthenticationInfrastructureIntegrationTest {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
		.withDatabaseName("openmd")
		.withUsername("openmd")
		.withPassword("openmd")
		.withStartupTimeout(Duration.ofMinutes(2));

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
		.withExposedPorts(6379)
		.waitingFor(Wait.forListeningPort())
		.withStartupTimeout(Duration.ofMinutes(1));

	@DynamicPropertySource
	static void infrastructureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
	}

	@Autowired JdbcTemplate jdbcTemplate;
	@Autowired UserRepository userRepository;
	@Autowired StringRedisTemplate redisTemplate;

	@BeforeEach
	void clearRedis() {
		redisTemplate.execute((RedisCallback<Void>) connection -> {
			connection.serverCommands().flushAll();
			return null;
		});
	}

	@Test
	void appliesFlywayMigrationAndEnforcesHibernateUniqueAndCheckContractsOnMySql84() {
		Integer migrationSucceeded = jdbcTemplate.queryForObject(
			"SELECT success FROM flyway_schema_history WHERE version = '1'",
			Integer.class
		);
		assertEquals(1, migrationSucceeded);

		User persisted = userRepository.saveAndFlush(User.pending(
			"learner@example.com",
			"learner@example.com",
			"$argon2id$test-hash"
		));
		assertNotNull(persisted.getId());
		assertNotNull(persisted.getCreatedAt());

		assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update("""
			INSERT INTO users (
			  email, normalized_email, password_hash, status, created_at, updated_at
			) VALUES (?, ?, ?, 'PENDING_ACTIVATION', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			""", "different@example.com", "learner@example.com", "$argon2id$another-hash"));

		assertThrows(DataAccessException.class, () -> jdbcTemplate.update("""
			INSERT INTO users (
			  email, normalized_email, password_hash, status, created_at, updated_at
			) VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			""", "invalid@example.com", "invalid@example.com", "$argon2id$invalid-hash"));
	}

	@Test
	void enforcesEmailVerificationTtlCooldownReplacementAndFiveFailureInvalidationInRedis74() {
		RedisEmailVerificationStore store = new RedisEmailVerificationStore(redisTemplate);
		Instant now = Instant.now();
		Duration ttl = Duration.ofSeconds(20);
		Duration cooldown = Duration.ofSeconds(60);
		String key = RedisEmailVerificationStore.key(42L);

		assertTrue(store.issue(42L, "digest-one", now, ttl, cooldown, false).issued());
		assertTtlWithin(key, ttl);
		assertEquals("digest-one", redisTemplate.opsForHash().get(key, "codeDigest"));

		EmailVerificationStore.IssueResult limited = store.issue(
			42L, "digest-two", now.plusSeconds(30), ttl, cooldown, true
		);
		assertFalse(limited.issued());
		assertEquals(30L, limited.retryAfterSeconds());
		assertEquals("digest-one", redisTemplate.opsForHash().get(key, "codeDigest"));

		assertTrue(store.issue(42L, "digest-two", now.plusSeconds(61), ttl, cooldown, true).issued());
		assertEquals("digest-two", redisTemplate.opsForHash().get(key, "codeDigest"));
		assertEquals(EmailVerificationStore.VerificationResult.MATCHED, store.verify(42L, "digest-two"));

		for (int attempt = 1; attempt <= 4; attempt++) {
			assertEquals(EmailVerificationStore.VerificationResult.MISMATCHED, store.verify(42L, "wrong"));
		}
		assertEquals(EmailVerificationStore.VerificationResult.EXPIRED, store.verify(42L, "wrong"));
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(key)));
	}

	@Test
	void cancelsOnlyTheFailedMailIssueAndImmediatelyReleasesItsCooldownInRedis74() {
		RedisEmailVerificationStore store = new RedisEmailVerificationStore(redisTemplate);
		Instant now = Instant.now();
		String key = RedisEmailVerificationStore.key(43L);

		assertTrue(store.issue(
			43L, "delivered-by-newer-request", now, Duration.ofMinutes(10), Duration.ofSeconds(60), false
		).issued());
		assertFalse(store.cancelIssue(43L, "stale-failed-digest"));
		assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(key)));

		assertTrue(store.cancelIssue(43L, "delivered-by-newer-request"));
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(key)));
		assertTrue(store.issue(
			43L, "retry-digest", now.plusSeconds(1), Duration.ofMinutes(10), Duration.ofSeconds(60), true
		).issued());
	}

	@Test
	void rotatesRefreshTokensCreatesTombstonesAndRevokesTheSessionOnReuseInRedis74() throws Exception {
		RedisRefreshSessionStore store = new RedisRefreshSessionStore(redisTemplate);
		RefreshTokenService service = new RefreshTokenService(store, Clock.systemUTC(), Duration.ofSeconds(20));
		IssuedRefreshToken first = service.issue(7L);
		String sessionKey = RedisRefreshSessionStore.sessionKey(first.sessionId());
		assertTtlWithin(sessionKey, Duration.ofSeconds(20));

		String firstSecret = first.token().split("\\.", -1)[1];
		String firstDigest = digest(firstSecret);
		String storedDigest = (String) redisTemplate.opsForHash().get(sessionKey, "currentTokenDigest");
		assertEquals(firstDigest, storedDigest);
		assertNotEquals(firstSecret, storedDigest);

		var rotated = service.rotate(first.token());
		String tombstoneKey = RedisRefreshSessionStore.usedKey(first.sessionId(), firstDigest);
		assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(tombstoneKey)));
		assertTtlWithin(tombstoneKey, Duration.ofSeconds(20));
		assertNotEquals(firstDigest, redisTemplate.opsForHash().get(sessionKey, "currentTokenDigest"));
		assertEquals(first.expiresAt(), rotated.refreshToken().expiresAt());

		BusinessException reused = assertThrows(BusinessException.class, () -> service.rotate(first.token()));
		assertEquals(AuthErrorCode.INVALID_CREDENTIAL, reused.getErrorCode());
		assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey)));
		assertTrue(Boolean.TRUE.equals(redisTemplate.hasKey(tombstoneKey)));
	}

	private void assertTtlWithin(String key, Duration expectedMaximum) {
		Long ttlMillis = redisTemplate.getExpire(key, java.util.concurrent.TimeUnit.MILLISECONDS);
		assertNotNull(ttlMillis);
		assertTrue(ttlMillis > 0, () -> "Expected a positive TTL for " + key + " but was " + ttlMillis);
		assertTrue(ttlMillis <= expectedMaximum.toMillis(),
			() -> "Expected TTL <= " + expectedMaximum.toMillis() + " but was " + ttlMillis);
	}

	private String digest(String secret) throws Exception {
		byte[] value = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.US_ASCII));
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}
}
