package com.openmd.server.push.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.auth.dto.model.IssuedRefreshToken;
import com.openmd.server.auth.dto.model.RefreshTokenSession;
import com.openmd.server.auth.service.AccountWithdrawalService;
import com.openmd.server.auth.service.AuthService;
import com.openmd.server.auth.service.RefreshTokenService;
import com.openmd.server.push.domain.PushDeviceStatus;
import com.openmd.server.push.domain.PushPermission;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.dto.command.RegisterPushDeviceCommand;
import com.openmd.server.push.dto.command.RevokePushDeviceCommand;
import com.openmd.server.push.dto.response.PushDeviceRegistrationResult;
import com.openmd.server.push.error.PushErrorCode;
import com.openmd.server.push.service.PushDeviceService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

@Testcontainers
@Tag("integration")
@SpringBootTest(
    properties = {
      "openmd.auth.enabled=true",
      "openmd.auth.access-token-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "openmd.auth.email-code-hmac-secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
      "openmd.mail.from=test@example.com",
      "spring.mail.host=localhost",
      "openmd.push.registration-enabled=true",
      "spring.jpa.open-in-view=false"
    })
@Import(PushDeviceInfrastructureIntegrationTest.FixedClockConfiguration.class)
class PushDeviceInfrastructureIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-09-06T06:00:00Z");
  private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
  private static final String TOKEN = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]";

  @Container
  static final MySQLContainer MYSQL =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("openmd_push")
          .withUsername("openmd")
          .withPassword("openmd")
          .withStartupTimeout(Duration.ofMinutes(2));

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
          .withExposedPorts(6379)
          .waitingFor(Wait.forListeningPort())
          .withStartupTimeout(Duration.ofMinutes(1));

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired StringRedisTemplate redis;
  @Autowired PushDeviceService service;
  @Autowired PushDeviceRepository devices;
  @Autowired PushRateLimitStore rateLimits;
  @Autowired RefreshTokenService refreshTokens;
  @Autowired AuthService authService;
  @Autowired AccountWithdrawalService withdrawalService;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    jdbc.update("DELETE FROM push_device_operations");
    jdbc.update("DELETE FROM push_devices");
    jdbc.update("DELETE FROM users");
    insertUser(42L, "one@example.com", "Study42");
    insertUser(84L, "two@example.com", "Study84");
    redis.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushAll();
              return null;
            });
  }

  @Test
  void flywayCreatesSecretSafeTablesAndEnforcesActiveTokenUniqueness() {
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT success FROM flyway_schema_history WHERE version = '12'", Integer.class));

    PushDeviceRegistrationResult result = service.register(42L, "session-42", command("11111111-1111-4111-8111-111111111111", "33333333-3333-4333-8333-333333333333", TOKEN));

    assertEquals(PushDeviceStatus.ACTIVE, result.status());
    assertFalse(
        jdbc.queryForObject(
                "SELECT installation_key_digest = ? FROM push_devices WHERE installation_id = ?",
                Boolean.class,
                KEY,
                result.installationId()));
    assertFalse(
        jdbc.queryForObject(
                "SELECT request_digest = ? FROM push_device_operations WHERE operation_id = ?",
                Boolean.class,
                TOKEN,
                "33333333-3333-4333-8333-333333333333"));
  }

  @Test
  void replaysOneConcurrentCreateAndKeepsExactlyOneInstallationRow() throws Exception {
    String installationId = "11111111-1111-4111-8111-111111111111";
    RegisterPushDeviceCommand command =
        command(installationId, "33333333-3333-4333-8333-333333333333", TOKEN);
    CountDownLatch start = new CountDownLatch(1);
    Callable<PushDeviceRegistrationResult> registration =
        () -> {
          start.await(5, TimeUnit.SECONDS);
          return service.register(42L, "session-42", command);
        };
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<PushDeviceRegistrationResult> first = pool.submit(registration);
      Future<PushDeviceRegistrationResult> second = pool.submit(registration);
      start.countDown();

      assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM push_devices WHERE installation_id = ?",
              Integer.class,
              installationId));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void lockedRegistrationRefreshesAnAlreadyManagedDeviceAfterConcurrentRevoke() {
    String installationId = "11111111-1111-4111-8111-111111111111";
    var registered = service.register(42L, "session-42",
        command(installationId, "33333333-3333-4333-8333-333333333333", TOKEN));
    var update = new RegisterPushDeviceCommand(installationId, KEY,
        "44444444-4444-4444-8444-444444444444", NOW, registered.revision(),
        PushPlatform.IOS, PushProvider.EXPO, TOKEN, PushPermission.GRANTED);
    var revoke = new RevokePushDeviceCommand(installationId, KEY,
        "55555555-5555-4555-8555-555555555555", NOW,
        registered.bindingId(), registered.revision());
    try (var pool = Executors.newSingleThreadExecutor()) {
      new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
        // Populate the first-level cache and establish the MySQL repeatable-read snapshot.
        assertEquals(PushDeviceStatus.ACTIVE,
            devices.findByInstallationId(installationId).orElseThrow().getStatus());
        try {
          assertTrue(pool.submit(() -> service.revoke(revoke)).get(10, TimeUnit.SECONDS).revoked());
        } catch (Exception exception) {
          throw new AssertionError("Concurrent revoke did not commit", exception);
        }
        var conflict = assertThrows(BusinessException.class,
            () -> service.register(42L, "session-42", update));
        assertEquals(PushErrorCode.REVISION_CONFLICT, conflict.getErrorCode());
        status.setRollbackOnly();
      });
    }
    assertEquals("REVOKED", jdbc.queryForObject(
        "SELECT status FROM push_devices WHERE installation_id = ?", String.class, installationId));
  }

  @Test
  void reinstallMovesTheSameUsersTokenButNeverTakesAnotherUsersToken() {
    service.register(
        42L,
        "old-session",
        command(
            "11111111-1111-4111-8111-111111111111",
            "33333333-3333-4333-8333-333333333333",
            TOKEN));

    PushDeviceRegistrationResult moved =
        service.register(
            42L,
            "new-session",
            command(
                "22222222-2222-4222-8222-222222222222",
                "44444444-4444-4444-8444-444444444444",
                TOKEN));

    assertEquals(PushDeviceStatus.ACTIVE, moved.status());
    assertEquals(
        List.of("REVOKED", "ACTIVE"),
        jdbc.queryForList("SELECT status FROM push_devices ORDER BY installation_id", String.class));

    BusinessException conflict =
        assertThrows(
            BusinessException.class,
            () ->
                service.register(
                    84L,
                    "other-session",
                    command(
                        "55555555-5555-4555-8555-555555555555",
                        "66666666-6666-4666-8666-666666666666",
                        TOKEN)));
    assertEquals(PushErrorCode.TOKEN_CONFLICT, conflict.getErrorCode());
  }

  @Test
  void redisLimiterUsesAnAtomicFixedWindowWithARealRedisServer() {
    for (int request = 1; request <= 20; request++) {
      assertEquals(0L, rateLimits.consume("installation", "device-42", 20, 60));
    }
    long retryAfter = rateLimits.consume("installation", "device-42", 20, 60);

    assertTrue(retryAfter > 0 && retryAfter <= 60);
  }

  @Test
  void verifiedLogoutRevokesOnlyDevicesBoundToThatRefreshSession() {
    IssuedRefreshToken refresh = refreshTokens.issue(42L);
    service.register(
        42L,
        refresh.sessionId(),
        command(
            "11111111-1111-4111-8111-111111111111",
            "33333333-3333-4333-8333-333333333333",
            TOKEN));

    authService.logout(refresh.token());

    var device =
        devices.findByInstallationId("11111111-1111-4111-8111-111111111111").orElseThrow();
    assertEquals(PushDeviceStatus.REVOKED, device.getStatus());
    assertEquals(null, device.getPushToken());
    assertThrows(BusinessException.class, () -> refreshTokens.inspect(refresh.token()));
  }

  @Test
  void withdrawalDeletesPushCredentialsAndOperationsInTheSameDatabaseTransaction() {
    jdbc.update(
        "UPDATE users SET password_hash = ? WHERE id = 42",
        passwordEncoder.encode("password1"));
    service.register(
        42L,
        "session-42",
        command(
            "11111111-1111-4111-8111-111111111111",
            "33333333-3333-4333-8333-333333333333",
            TOKEN));

    withdrawalService.withdraw(
        42L,
        "77777777-7777-4777-8777-777777777777",
        "password1",
        "회원탈퇴");

    assertEquals(
        "WITHDRAWN", jdbc.queryForObject("SELECT status FROM users WHERE id = 42", String.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM push_devices WHERE user_id = 42", Integer.class));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM push_device_operations WHERE subject_user_id = 42",
            Integer.class));
  }

  private RegisterPushDeviceCommand command(
      String installationId, String operationId, String token) {
    return new RegisterPushDeviceCommand(
        installationId,
        KEY,
        operationId,
        NOW,
        0L,
        PushPlatform.IOS,
        PushProvider.EXPO,
        token,
        PushPermission.GRANTED);
  }

  private void insertUser(long id, String email, String nickname) {
    jdbc.update(
        """
        INSERT INTO users (
          id, email, normalized_email, password_hash, nickname, email_verified_at,
          service_terms_version, service_terms_agreed_at,
          privacy_terms_version, privacy_terms_agreed_at,
          status, activated_at, created_at, updated_at
        ) VALUES (?, ?, ?, 'hash', ?, NOW(6), '2026-09-04', NOW(6),
          '2026-09-04', NOW(6), 'ACTIVE', NOW(6), NOW(6), NOW(6))
        """,
        id,
        email,
        email,
        nickname);
  }

  @TestConfiguration
  static class FixedClockConfiguration {
    @Bean
    @Primary
    Clock fixedPushClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
