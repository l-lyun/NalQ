package com.openmd.server.push.repository.redis;

import com.openmd.server.push.repository.PushRateLimitStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public final class RedisPushRateLimitStore implements PushRateLimitStore {

  static final DefaultRedisScript<Long> CONSUME_SCRIPT =
      new DefaultRedisScript<>(
          """
          local count = redis.call('INCR', KEYS[1])
          if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
          if count <= tonumber(ARGV[1]) then return 0 end
          local ttl = redis.call('TTL', KEYS[1])
          if ttl < 1 then return 1 end
          return ttl
          """,
          Long.class);

  private final StringRedisTemplate redis;

  public RedisPushRateLimitStore(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public long consume(String scope, String subject, int limit, int windowSeconds) {
    Long result =
        redis.execute(
            CONSUME_SCRIPT,
            List.of(key(scope, subject)),
            Integer.toString(limit),
            Integer.toString(windowSeconds));
    if (result == null) {
      throw new IllegalStateException("Redis did not return a rate limit result");
    }
    return result;
  }

  static String key(String scope, String subject) {
    return "push:rate-limit:" + scope + ":{" + sha256(subject) + "}";
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
