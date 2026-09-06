package com.openmd.server.push.config;

import com.openmd.server.push.repository.PushRateLimitStore;
import com.openmd.server.push.repository.PushDeliveryClaimStore;
import com.openmd.server.push.repository.PushRetentionStore;
import com.openmd.server.push.repository.redis.RedisPushRateLimitStore;
import com.openmd.server.push.security.PushInstallationCredential;
import com.openmd.server.push.service.PushBindingIdSupplier;
import com.openmd.server.push.service.PushDeliveryPolicy;
import com.openmd.server.push.service.PushDeliveryTransaction;
import com.openmd.server.push.service.PushDeliveryWorker;
import com.openmd.server.push.service.PushGateway;
import com.openmd.server.push.service.PushRetentionService;
import com.openmd.server.push.integration.expo.ExpoPushGateway;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

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

  @Bean
  @ConditionalOnExpression(
      "'${openmd.push.delivery-enabled:false}' == 'true' or '${openmd.push.scheduler-enabled:false}' == 'true'")
  PushDeliveryClaimStore pushDeliveryClaimStore(ObjectProvider<JdbcTemplate> jdbc) {
    return new PushDeliveryClaimStore(jdbc.getObject());
  }

  @Bean
  @ConditionalOnExpression(
      "'${openmd.push.delivery-enabled:false}' == 'true' or '${openmd.push.scheduler-enabled:false}' == 'true'")
  PushRetentionStore pushRetentionStore(ObjectProvider<JdbcTemplate> jdbc) {
    return new PushRetentionStore(jdbc.getObject());
  }

  @Bean
  @ConditionalOnExpression(
      "'${openmd.push.delivery-enabled:false}' == 'true' or '${openmd.push.scheduler-enabled:false}' == 'true'")
  PushDeliveryPolicy pushDeliveryPolicy() {
    return new PushDeliveryPolicy(() -> ThreadLocalRandom.current().nextDouble());
  }

  @Bean
  @ConditionalOnExpression(
      "'${openmd.push.delivery-enabled:false}' == 'true' or '${openmd.push.scheduler-enabled:false}' == 'true'")
  PushDeliveryTransaction pushDeliveryTransaction(
      PushDeliveryClaimStore store, PushDeliveryPolicy policy) {
    return new PushDeliveryTransaction(store, policy);
  }

  @Bean
  @ConditionalOnExpression(
      "'${openmd.push.delivery-enabled:false}' == 'true' or '${openmd.push.scheduler-enabled:false}' == 'true'")
  PushRetentionService pushRetentionService(
      PushRetentionStore store, ObjectProvider<Clock> clock, PushProperties properties) {
    return new PushRetentionService(
        store, clock.getIfAvailable(Clock::systemUTC), properties.getRetentionBatchSize());
  }

  @Bean
  @ConditionalOnExpression(
      "'${openmd.push.delivery-enabled:false}' == 'true' or '${openmd.push.scheduler-enabled:false}' == 'true'")
  PushGateway pushGateway(
      ObjectMapper mapper, ObjectProvider<Clock> clock, PushProperties properties) {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    return new ExpoPushGateway(
        client,
        mapper,
        properties.getExpoApiBase(),
        properties.getExpoAccessToken(),
        Duration.ofSeconds(10),
        clock.getIfAvailable(Clock::systemUTC));
  }

  @Bean
  @ConditionalOnExpression(
      "'${openmd.push.delivery-enabled:false}' == 'true' or '${openmd.push.scheduler-enabled:false}' == 'true'")
  PushDeliveryWorker pushDeliveryWorker(
      PushDeliveryTransaction transactions,
      PushGateway gateway,
      ObjectProvider<Clock> clock,
      PushProperties properties) {
    return new PushDeliveryWorker(
        transactions,
        gateway,
        clock.getIfAvailable(Clock::systemUTC),
        properties.getBatchSize(),
        properties.getLeaseDuration());
  }
}
