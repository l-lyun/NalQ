package com.openmd.server.integration.notion.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.integration.notion.crypto.AesGcmTokenCipher;
import com.openmd.server.integration.notion.crypto.TokenType;
import com.openmd.server.integration.notion.crypto.TokenCipher;
import com.openmd.server.integration.notion.domain.NotionConnection;
import com.openmd.server.integration.notion.client.NotionClient;
import com.openmd.server.integration.notion.client.NotionMarkdown;
import com.openmd.server.integration.notion.client.NotionPage;
import com.openmd.server.integration.notion.client.NotionPageSearch;
import com.openmd.server.integration.notion.client.NotionTokenGrant;
import com.openmd.server.integration.notion.dto.model.NotionOAuthState;
import com.openmd.server.integration.notion.repository.redis.RedisNotionOAuthStateStore;
import com.openmd.server.integration.notion.service.NotionConnectionService;
import com.openmd.server.integration.notion.service.NotionMarkdownProcessor;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@Tag("integration")
@SpringBootTest(properties = {"openmd.auth.enabled=false", "openmd.notion.enabled=false", "spring.jpa.open-in-view=false"})
@Import(NotionInfrastructureIntegrationTest.NotionTestConfiguration.class)
class NotionInfrastructureIntegrationTest {
	@Container
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
		.withDatabaseName("openmd_notion").withUsername("openmd").withPassword("openmd")
		.withStartupTimeout(Duration.ofMinutes(2));
	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
		.withExposedPorts(6379).waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofMinutes(1));

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
	}

	@Autowired JdbcTemplate jdbc;
	@Autowired UserRepository users;
	@Autowired NotionConnectionRepository connections;
	@Autowired StringRedisTemplate redis;
	@Autowired ObjectMapper mapper;
	@Autowired NotionOAuthStateStore stateStore;
	@Autowired NotionConnectionService service;
	@Autowired BlockingNotionClient notionClient;

	@BeforeEach
	void clear() {
		connections.deleteAll();
		users.deleteAll();
		redis.execute((RedisCallback<Void>) connection -> { connection.serverCommands().flushAll(); return null; });
		notionClient.reset();
	}

	@Test
	void migrationCreatesOneConnectionPerUserAndPreservesNullableWorkspaceNameAndRefreshToken() {
		assertEquals(1, jdbc.queryForObject(
			"SELECT success FROM flyway_schema_history WHERE version = '8'", Integer.class
		));
		User user = users.saveAndFlush(User.pending("notion@example.com", "notion@example.com", "hash"));
		AesGcmTokenCipher cipher = new AesGcmTokenCipher(Map.of("v1", new byte[32]), "v1", new SecureRandom());
		var first = NotionConnection.connected(user.getId(), "workspace-a", null,
			cipher.encrypt(user.getId(), "workspace-a", TokenType.ACCESS, "access"), null, Instant.now());
		connections.saveAndFlush(first);

		assertEquals(null, connections.findByUserId(user.getId()).orElseThrow().getWorkspaceName());
		var duplicate = NotionConnection.connected(user.getId(), "workspace-b", "other",
			cipher.encrypt(user.getId(), "workspace-b", TokenType.ACCESS, "other"), null, Instant.now());
		assertThrows(DataIntegrityViolationException.class, () -> connections.saveAndFlush(duplicate));
	}

	@Test
	void redisConsumesStateOnceByDigestAndDisconnectInvalidatesEveryOutstandingState() {
		RedisNotionOAuthStateStore store = new RedisNotionOAuthStateStore(redis, mapper);
		NotionOAuthState state = new NotionOAuthState(
			7L, "https://openmd.test/import", "CONNECT", Instant.now(), null, null
		);
		store.save("raw-secret-state", state, Duration.ofMinutes(15));

		assertTrue(redis.keys("notion:oauth:state:*").stream().noneMatch(key -> key.contains("raw-secret-state")));
		assertEquals(state, store.find("raw-secret-state").orElseThrow());
		assertEquals(state, store.consume("raw-secret-state").orElseThrow());
		assertTrue(store.consume("raw-secret-state").isEmpty());

		store.save("another-state", state, Duration.ofMinutes(15));
		store.invalidateUser(7L);
		assertFalse(store.consume("another-state").isPresent());
	}

	@Test
	void serializesConcurrentInitialConnectCallbacksOnTheStableUserRow() throws Exception {
		User user = users.saveAndFlush(User.pending("callbacks@example.com", "callbacks@example.com", "hash"));
		Instant now = Instant.parse("2026-09-01T00:00:00Z");
		NotionOAuthState initial = new NotionOAuthState(
			user.getId(), "https://openmd.test/import", "CONNECT", now, null, null
		);
		stateStore.save("state-one", initial, Duration.ofMinutes(15));
		stateStore.save("state-two", initial, Duration.ofMinutes(15));
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<String> first = executor.submit(() -> service.completeAuthorization("state-one", "code-one", null));
			assertTrue(notionClient.firstExchangeEntered.await(5, TimeUnit.SECONDS));
			Future<String> second = executor.submit(() -> service.completeAuthorization("state-two", "code-two", null));
			notionClient.releaseFirst.countDown();

			assertEquals("https://openmd.test/import?outcome=connected", first.get(10, TimeUnit.SECONDS));
			assertEquals(
				"https://openmd.test/import?outcome=failed&error=NOTION_CONNECTION_REQUIRED",
				second.get(10, TimeUnit.SECONDS)
			);
			assertEquals(1, notionClient.exchangeCalls.get());
			assertEquals("workspace-a", connections.findByUserId(user.getId()).orElseThrow().getWorkspaceId());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void disconnectInvalidatesAnUnconsumedInitialStateSoALateCallbackCannotReconnect() {
		User user = users.saveAndFlush(User.pending("late@example.com", "late@example.com", "hash"));
		stateStore.save("late-state", new NotionOAuthState(
			user.getId(), "https://openmd.test/import", "CONNECT", Instant.now(), null, null
		), Duration.ofMinutes(15));

		assertEquals("DISCONNECTED", service.disconnect(user.getId()).status());
		assertEquals(
			"https://openmd.test/import?outcome=failed&error=NOTION_CONNECTION_REQUIRED",
			service.completeAuthorization("late-state", "late-code", null)
		);
		assertEquals(0, notionClient.exchangeCalls.get());
		assertTrue(connections.findByUserId(user.getId()).isEmpty());
	}

	@TestConfiguration
	static class NotionTestConfiguration {
		@Bean Clock notionTestClock() { return Clock.systemUTC(); }
		@Bean TokenCipher notionTestCipher() {
			return new AesGcmTokenCipher(Map.of("v1", new byte[32]), "v1", new SecureRandom());
		}
		@Bean NotionOAuthStateStore notionTestStateStore(StringRedisTemplate redis, ObjectMapper mapper) {
			return new RedisNotionOAuthStateStore(redis, mapper);
		}
		@Bean BlockingNotionClient notionTestClient() { return new BlockingNotionClient(); }
		@Bean NotionConnectionService notionTestService(
			NotionConnectionRepository connections, NotionOAuthStateStore states, BlockingNotionClient client,
			TokenCipher cipher, UserRepository users, Clock clock
		) {
			return new NotionConnectionService(
				connections, states, client, cipher, users, new NotionMarkdownProcessor(), clock,
				java.util.List.of("https://openmd.test/import"), "https://openmd.test/import"
			);
		}
	}

	static final class BlockingNotionClient implements NotionClient {
		private AtomicInteger exchangeCalls;
		private CountDownLatch firstExchangeEntered;
		private CountDownLatch releaseFirst;

		BlockingNotionClient() { reset(); }
		void reset() {
			exchangeCalls = new AtomicInteger();
			firstExchangeEntered = new CountDownLatch(1);
			releaseFirst = new CountDownLatch(1);
		}
		@Override public String authorizationUrl(String state) { return "https://notion.test?state=" + state; }
		@Override public NotionTokenGrant exchangeAuthorizationCode(String code) {
			int call = exchangeCalls.incrementAndGet();
			if (call == 1) {
				firstExchangeEntered.countDown();
				try {
					if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("release timeout");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
			}
			return new NotionTokenGrant("access-" + code, null, "workspace-a", null);
		}
		@Override public NotionTokenGrant refresh(String refreshToken) { throw new UnsupportedOperationException(); }
		@Override public boolean revoke(String accessToken) { return true; }
		@Override public boolean introspect(String accessToken) { return false; }
		@Override public NotionPageSearch searchPages(String accessToken, String cursor, String query) { throw new UnsupportedOperationException(); }
		@Override public NotionPage retrievePage(String accessToken, String pageId) { throw new UnsupportedOperationException(); }
		@Override public NotionMarkdown retrieveMarkdown(String accessToken, String pageId) { throw new UnsupportedOperationException(); }
	}
}
