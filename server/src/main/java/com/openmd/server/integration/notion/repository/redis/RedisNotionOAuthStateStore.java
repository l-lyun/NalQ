package com.openmd.server.integration.notion.repository.redis;

import com.openmd.server.integration.notion.dto.model.NotionOAuthState;
import com.openmd.server.integration.notion.repository.NotionOAuthStateStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class RedisNotionOAuthStateStore implements NotionOAuthStateStore {
	private static final String PREFIX = "notion:oauth:state:";
	private static final String USER_PREFIX = "notion:oauth:user:";
	private final StringRedisTemplate redis;
	private final ObjectMapper mapper;

	public RedisNotionOAuthStateStore(StringRedisTemplate redis, ObjectMapper mapper) {
		this.redis = redis;
		this.mapper = mapper;
	}

	@Override
	public void save(String rawState, NotionOAuthState state, Duration ttl) {
		String digest = digest(rawState);
		try {
			redis.opsForValue().set(PREFIX + digest, mapper.writeValueAsString(state), ttl);
			String userKey = USER_PREFIX + state.userId();
			redis.opsForSet().add(userKey, digest);
			redis.expire(userKey, ttl);
		} catch (JacksonException exception) {
			throw new IllegalStateException("Could not serialize Notion OAuth state", exception);
		}
	}

	@Override
	public Optional<NotionOAuthState> find(String rawState) {
		String json = redis.opsForValue().get(PREFIX + digest(rawState));
		return deserialize(json);
	}

	@Override
	public Optional<NotionOAuthState> consume(String rawState) {
		String digest = digest(rawState);
		String json = redis.opsForValue().getAndDelete(PREFIX + digest);
		Optional<NotionOAuthState> parsed = deserialize(json);
		if (parsed.isPresent()) {
			NotionOAuthState state = parsed.get();
			redis.opsForSet().remove(USER_PREFIX + state.userId(), digest);
		}
		return parsed;
	}

	@Override
	public void invalidateUser(long userId) {
		String userKey = USER_PREFIX + userId;
		Set<String> digests = redis.opsForSet().members(userKey);
		if (digests != null && !digests.isEmpty()) {
			redis.delete(digests.stream().map(value -> PREFIX + value).toList());
		}
		redis.delete(userKey);
	}

	private static String digest(String rawState) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(rawState.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private Optional<NotionOAuthState> deserialize(String json) {
		if (json == null) return Optional.empty();
		try {
			return Optional.of(mapper.readValue(json, NotionOAuthState.class));
		} catch (JacksonException exception) {
			return Optional.empty();
		}
	}
}
