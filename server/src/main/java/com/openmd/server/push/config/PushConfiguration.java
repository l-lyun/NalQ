package com.openmd.server.push.config;

import com.openmd.server.push.repository.PushRateLimitStore;
import com.openmd.server.push.repository.redis.RedisPushRateLimitStore;
import com.openmd.server.push.security.PushInstallationCredential;
import com.openmd.server.push.service.PushBindingIdSupplier;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@EnableConfigurationProperties(PushProperties.class)
public class PushConfiguration {

  @Bean
  PushInstallationCredential pushInstallationCredential() {
    return new PushInstallationCredential();
  }

  @Bean
  PushBindingIdSupplier pushBindingIdSupplier() {
    return UUID::randomUUID;
  }

  @Bean
  PushRateLimitStore pushRateLimitStore(ObjectProvider<StringRedisTemplate> redisProvider) {
    return (scope, subject, limit, windowSeconds) -> {
      StringRedisTemplate redis = redisProvider.getIfAvailable();
      if (redis == null) {
        throw new IllegalStateException("Redis is unavailable for push device rate limiting");
      }
      return new RedisPushRateLimitStore(redis).consume(scope, subject, limit, windowSeconds);
    };
  }
}
