package com.openmd.server.auth.repository.redis;

import com.openmd.server.auth.repository.RefreshSessionStore;
import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisRefreshSessionStore implements RefreshSessionStore {

	static final DefaultRedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>("""
		redis.call('HSET', KEYS[1],
		  'userId', ARGV[1],
		  'familyId', ARGV[2],
		  'currentTokenDigest', ARGV[3],
		  'status', 'ACTIVE',
		  'absoluteExpiresAt', ARGV[4])
		redis.call('PEXPIREAT', KEYS[1], ARGV[4])
		return 1
		""", Long.class);

	static final DefaultRedisScript<String> ROTATE_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('EXISTS', KEYS[2]) == 1 then
		  redis.call('DEL', KEYS[1])
		  return 'REUSED'
		end
		if redis.call('EXISTS', KEYS[1]) == 0 then return 'INVALID' end
		if redis.call('HGET', KEYS[1], 'status') ~= 'ACTIVE' then return 'INVALID' end
		if redis.call('HGET', KEYS[1], 'currentTokenDigest') ~= ARGV[1] then return 'INVALID' end
		local userId = redis.call('HGET', KEYS[1], 'userId')
		local familyId = redis.call('HGET', KEYS[1], 'familyId')
		local expiresAt = redis.call('HGET', KEYS[1], 'absoluteExpiresAt')
		redis.call('HSET', KEYS[2], 'familyId', familyId)
		redis.call('PEXPIREAT', KEYS[2], expiresAt)
		redis.call('HSET', KEYS[1], 'currentTokenDigest', ARGV[2])
		return userId .. ':' .. expiresAt
		""", String.class);

	static final DefaultRedisScript<String> INSPECT_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('EXISTS', KEYS[2]) == 1 then
		  redis.call('DEL', KEYS[1])
		  return 'REUSED'
		end
		if redis.call('EXISTS', KEYS[1]) == 0 then return 'INVALID' end
		if redis.call('HGET', KEYS[1], 'status') ~= 'ACTIVE' then return 'INVALID' end
		if redis.call('HGET', KEYS[1], 'currentTokenDigest') ~= ARGV[1] then return 'INVALID' end
		local userId = redis.call('HGET', KEYS[1], 'userId')
		local expiresAt = redis.call('HGET', KEYS[1], 'absoluteExpiresAt')
		return userId .. ':' .. expiresAt
		""", String.class);

	private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
		if redis.call('EXISTS', KEYS[2]) == 1 then
		  redis.call('DEL', KEYS[1])
		  return 1
		end
		if redis.call('HGET', KEYS[1], 'currentTokenDigest') == ARGV[1] then
		  redis.call('DEL', KEYS[1])
		  return 1
		end
		return 0
		""", Long.class);

	private final StringRedisTemplate redisTemplate;

	public RedisRefreshSessionStore(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	@Override
	public void create(String sessionId, long userId, String familyId, String digest, Instant expiresAt) {
		redisTemplate.execute(
			CREATE_SCRIPT,
			List.of(sessionKey(sessionId)),
			Long.toString(userId),
			familyId,
			digest,
			Long.toString(expiresAt.toEpochMilli())
		);
	}

	@Override
	public InspectionResult inspect(String sessionId, String currentDigest) {
		String result = redisTemplate.execute(
			INSPECT_SCRIPT,
			List.of(sessionKey(sessionId), usedKey(sessionId, currentDigest)),
			currentDigest
		);
		if (result == null || result.equals("INVALID")) {
			return InspectionResult.invalid();
		}
		if (result.equals("REUSED")) {
			return InspectionResult.reused();
		}
		String[] values = result.split(":", 2);
		return InspectionResult.valid(Long.parseLong(values[0]), Instant.ofEpochMilli(Long.parseLong(values[1])));
	}

	@Override
	public RotationResult rotate(String sessionId, String currentDigest, String newDigest) {
		String result = redisTemplate.execute(
			ROTATE_SCRIPT,
			List.of(sessionKey(sessionId), usedKey(sessionId, currentDigest)),
			currentDigest,
			newDigest
		);
		if (result == null || result.equals("INVALID")) {
			return RotationResult.invalid();
		}
		if (result.equals("REUSED")) {
			return RotationResult.reused();
		}
		String[] values = result.split(":", 2);
		return RotationResult.rotated(Long.parseLong(values[0]), Instant.ofEpochMilli(Long.parseLong(values[1])));
	}

	@Override
	public void revoke(String sessionId, String digest) {
		redisTemplate.execute(
			REVOKE_SCRIPT,
			List.of(sessionKey(sessionId), usedKey(sessionId, digest)),
			digest
		);
	}

	static String sessionKey(String sessionId) {
		return "auth:session:{" + sessionId + "}";
	}

	static String usedKey(String sessionId, String digest) {
		return "auth:refresh-used:{" + sessionId + "}:" + digest;
	}
}
