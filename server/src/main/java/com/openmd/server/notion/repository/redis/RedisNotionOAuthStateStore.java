package com.openmd.server.notion.repository.redis;

import com.openmd.server.notion.dto.model.NotionOAuthState;
import com.openmd.server.notion.repository.NotionOAuthStateStore;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisNotionOAuthStateStore implements NotionOAuthStateStore {

	static final DefaultRedisScript<String> CONSUME_SCRIPT = new DefaultRedisScript<>("""
		local userId = redis.call('GET', KEYS[1])
		if not userId then
		  return nil
		end
		redis.call('DEL', KEYS[1])
		return userId
		""", String.class);

	private final StringRedisTemplate redis;

	public RedisNotionOAuthStateStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public void save(String stateDigest, NotionOAuthState state, Duration ttl) {
		redis.opsForValue().set(key(stateDigest), Long.toString(state.userId()), ttl);
	}

	@Override
	public Optional<NotionOAuthState> consume(String stateDigest) {
		String userId = redis.execute(CONSUME_SCRIPT, List.of(key(stateDigest)));
		if (userId == null) {
			return Optional.empty();
		}
		return Optional.of(new NotionOAuthState(Long.parseLong(userId)));
	}

	static String key(String stateDigest) {
		return "notion:oauth-state:{" + stateDigest + "}";
	}
}
