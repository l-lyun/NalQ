package com.openmd.server.notion.repository.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.notion.dto.model.NotionOAuthState;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class NotionOAuthStateRedisIntegrationTest {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
		.withExposedPorts(6379)
		.withStartupTimeout(Duration.ofMinutes(1));

	private static LettuceConnectionFactory connectionFactory;
	private static StringRedisTemplate redis;

	@BeforeAll
	static void connect() {
		connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
			REDIS.getHost(), REDIS.getMappedPort(6379)
		));
		connectionFactory.afterPropertiesSet();
		redis = new StringRedisTemplate(connectionFactory);
		redis.afterPropertiesSet();
	}

	@AfterAll
	static void disconnect() {
		connectionFactory.destroy();
	}

	@Test
	void storesWithTtlAndAllowsExactlyOneConsumer() {
		RedisNotionOAuthStateStore store = new RedisNotionOAuthStateStore(redis);
		Duration ttl = Duration.ofMinutes(10);
		String digest = "digest-42";

		store.save(digest, new NotionOAuthState(7L), ttl);

		Long ttlMillis = redis.getExpire(RedisNotionOAuthStateStore.key(digest), TimeUnit.MILLISECONDS);
		assertTrue(ttlMillis != null && ttlMillis > 0 && ttlMillis <= ttl.toMillis());
		assertEquals(7L, store.consume(digest).orElseThrow().userId());
		assertTrue(store.consume(digest).isEmpty());
	}
}
