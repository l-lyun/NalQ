package com.openmd.server.push.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.push.dto.model.PushGatewayResult;
import com.openmd.server.push.dto.model.PushGatewayResult.Outcome;
import com.openmd.server.push.service.PushDeliveryTransaction;
import com.openmd.server.push.service.PushRetentionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
@SpringBootTest(
    properties = {
      "openmd.auth.enabled=false",
      "openmd.quiz.enabled=false",
      "openmd.home-visit.enabled=false",
      "openmd.push.registration-enabled=false",
      "openmd.push.delivery-enabled=true",
      "openmd.push.scheduler-enabled=false",
      "spring.jpa.open-in-view=false"
    })
@Import(PushDeliveryClaimStoreIntegrationTest.FixedClockConfiguration.class)
class PushDeliveryClaimStoreIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-09-06T06:00:00Z");

  @Container
  static final MySQLContainer MYSQL =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("openmd_push_claim")
          .withUsername("openmd")
          .withPassword("openmd")
          .withStartupTimeout(Duration.ofMinutes(2));

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired PushDeliveryTransaction transactions;
  @Autowired PushRetentionService retention;
  @Autowired PlatformTransactionManager transactionManager;

  @BeforeEach
  void setUp() {
    jdbc.update("DELETE FROM push_deliveries");
    jdbc.update("DELETE FROM push_device_operations");
    jdbc.update("DELETE FROM push_devices");
    jdbc.update("DELETE FROM notifications");
    jdbc.update("DELETE FROM users");
    insertUser(42L);
    insertActiveDevice(101L, "binding-101", 1L, "ExponentPushToken[token101]");
  }

  @Test
  void twoWorkersClaimDifferentRowsAndAnOldAttemptCannotOverwriteANewerLease() throws Exception {
    insertNotification(201L, "notification-201");
    insertNotification(202L, "notification-202");
    insertDelivery(301L, 201L, "PENDING", 0, NOW, NOW.plusSeconds(3600), null, null);
    insertDelivery(302L, 202L, "PENDING", 0, NOW, NOW.plusSeconds(3600), null, null);
    CountDownLatch start = new CountDownLatch(1);
    Callable<List<com.openmd.server.push.dto.model.PushDeliveryAttempt>> claim =
        () -> {
          start.await(5, TimeUnit.SECONDS);
          return transactions.claimSend(NOW, 1, Duration.ofSeconds(60));
        };
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(claim);
      var second = pool.submit(claim);
      start.countDown();
      var firstClaim = first.get(10, TimeUnit.SECONDS).getFirst();
      var secondClaim = second.get(10, TimeUnit.SECONDS).getFirst();

      assertNotEquals(firstClaim.deliveryId(), secondClaim.deliveryId());
      transactions.recoverExpiredLeases(NOW.plusSeconds(61), 50);
      var newer =
          transactions
              .claimSend(NOW.plusSeconds(61), 1, Duration.ofSeconds(60))
              .stream()
              .filter(candidate -> candidate.deliveryId() == firstClaim.deliveryId())
              .findFirst()
              .orElseGet(
                  () ->
                      transactions
                          .claimSend(NOW.plusSeconds(61), 1, Duration.ofSeconds(60))
                          .getFirst());
      transactions.recordSendResult(
          firstClaim, PushGatewayResult.accepted("stale-ticket"), NOW.plusSeconds(62));
      assertEquals(
          newer.attemptId(),
          jdbc.queryForObject(
              "SELECT attempt_id FROM push_deliveries WHERE id = ?",
              String.class,
              newer.deliveryId()));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void expiredExhaustedAndOldReceiptRowsBecomeTerminalWithoutProviderClaims() {
    insertNotification(201L, "notification-201");
    insertNotification(202L, "notification-202");
    insertNotification(203L, "notification-203");
    insertDelivery(301L, 201L, "PENDING", 0, NOW.minusSeconds(4000), NOW.minusSeconds(1), null, null);
    insertDelivery(302L, 202L, "RETRY_WAIT", 8, NOW, NOW.plusSeconds(600), null, null);
    insertDelivery(
        303L,
        203L,
        "TICKET_ACCEPTED",
        1,
        NOW.minusSeconds(90000),
        NOW.plusSeconds(600),
        "ticket-old",
        NOW.minusSeconds(86400));

    assertTrue(transactions.claimSend(NOW, 50, Duration.ofSeconds(60)).isEmpty());
    assertTrue(transactions.claimReceipts(NOW, 50, Duration.ofSeconds(60)).isEmpty());

    assertEquals("EXPIRED", state(301L));
    assertEquals("FAILED", state(302L));
    assertEquals("UNKNOWN", state(303L));
  }

  @Test
  void invalidTokenResultCannotDeactivateADeviceAfterItsTokenVersionChanged() {
    insertNotification(201L, "notification-201");
    insertDelivery(301L, 201L, "PENDING", 0, NOW, NOW.plusSeconds(3600), null, null);
    var attempt = transactions.claimSend(NOW, 1, Duration.ofSeconds(60)).getFirst();
    assertTrue(transactions.prepareSend(attempt, NOW.plusSeconds(1)).isPresent());
    jdbc.update(
        """
        UPDATE push_devices
        SET token_version = 2,
            push_token = 'ExponentPushToken[token-new]',
            push_token_digest = REPEAT('b', 64)
        WHERE id = 101
        """);

    transactions.recordSendResult(
        attempt,
        new PushGatewayResult(
            Outcome.INVALID_TOKEN, null, "DEVICE_NOT_REGISTERED", Duration.ZERO),
        NOW.plusSeconds(2));

    assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM push_devices WHERE id=101", String.class));
    assertEquals(2L, jdbc.queryForObject("SELECT token_version FROM push_devices WHERE id=101", Long.class));
  }

  @Test
  void invalidTokenResultCannotDeactivateADeviceAfterItsBindingChanged() {
    insertNotification(201L, "notification-201");
    insertDelivery(301L, 201L, "PENDING", 0, NOW, NOW.plusSeconds(3600), null, null);
    var attempt = transactions.claimSend(NOW, 1, Duration.ofSeconds(60)).getFirst();
    assertTrue(transactions.prepareSend(attempt, NOW.plusSeconds(1)).isPresent());
    jdbc.update("UPDATE push_devices SET binding_id = 'binding-new' WHERE id = 101");

    transactions.recordSendResult(
        attempt,
        new PushGatewayResult(
            Outcome.INVALID_TOKEN, null, "DEVICE_NOT_REGISTERED", Duration.ZERO),
        NOW.plusSeconds(2));

    assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM push_devices WHERE id=101", String.class));
    assertEquals("binding-new", jdbc.queryForObject("SELECT binding_id FROM push_devices WHERE id=101", String.class));
  }

  @Test
  void lateInvalidReceiptCannotDeactivateANewerTokenVersion() {
    insertNotification(201L, "notification-201");
    insertDelivery(
        301L,
        201L,
        "TICKET_ACCEPTED",
        1,
        NOW.minusSeconds(901),
        NOW.plusSeconds(3600),
        "ticket-1",
        NOW.minusSeconds(901));
    var receipt = transactions.claimReceipts(NOW, 1, Duration.ofSeconds(60)).getFirst();
    jdbc.update(
        """
        UPDATE push_devices
        SET token_version = 2, push_token = 'ExponentPushToken[token-new]',
            push_token_digest = REPEAT('b', 64)
        WHERE id = 101
        """);

    transactions.recordReceiptResult(
        receipt,
        new PushGatewayResult(
            Outcome.INVALID_TOKEN, null, "DEVICE_NOT_REGISTERED", Duration.ZERO),
        NOW.plusSeconds(1));

    assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM push_devices WHERE id=101", String.class));
    assertEquals(2L, jdbc.queryForObject("SELECT token_version FROM push_devices WHERE id=101", Long.class));
  }

  @Test
  void leaseRecoveryIsBoundedAndAttemptEightEndsAsFailed() {
    insertNotification(201L, "notification-201");
    insertNotification(202L, "notification-202");
    insertDelivery(301L, 201L, "SENDING", 8, NOW, NOW.plusSeconds(600), null, null);
    insertDelivery(302L, 202L, "SENDING", 2, NOW, NOW.plusSeconds(600), null, null);
    jdbc.update(
        """
        UPDATE push_deliveries SET attempt_id='lost',
          lease_until=TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
        WHERE id IN (301,302)
        """,
        epochMicros(NOW.minusSeconds(1)));

    assertEquals(1, transactions.recoverExpiredLeases(NOW, 1));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM push_deliveries WHERE state='SENDING'", Integer.class));
    assertEquals("FAILED", state(301L));
  }

  @Test
  void retentionKeepsActiveAndBoundaryRowsButDeletesOnlyExpiredRows() {
    insertNotification(201L, "notification-201");
    insertDelivery(
        301L,
        201L,
        "PROVIDER_ACCEPTED",
        1,
        NOW.minusSeconds(30L * 86400L + 1),
        NOW.minusSeconds(29L * 86400L),
        "ticket-1",
        NOW.minusSeconds(29L * 86400L));
    jdbc.update(
        """
        INSERT INTO push_device_operations (
          installation_id, operation_id, operation_type, subject_user_id, request_digest,
          issued_at, result_revision, result_binding_id, result_status, result_user_id,
          expires_at, created_at, updated_at
        ) VALUES ('install-old', 'operation-old', 'REGISTER', 42, REPEAT('a',64),
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
          1, 'binding-old', 'ACTIVE', 42,
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'))
        """,
        epochMicros(NOW.minusSeconds(8L * 86400L)),
        epochMicros(NOW.minusSeconds(1)),
        epochMicros(NOW.minusSeconds(8L * 86400L)),
        epochMicros(NOW.minusSeconds(8L * 86400L)));
    insertInactiveDevice(102L, NOW.minusSeconds(30L * 86400L + 1));
    insertInactiveDevice(103L, NOW.minusSeconds(30L * 86400L));

    retention.deleteExpired();

    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM push_deliveries WHERE id=301", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM push_device_operations", Integer.class));
    assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM push_devices WHERE id=102", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM push_devices WHERE id=103", Integer.class));
    assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM push_devices WHERE id=101", Integer.class));
  }

  @Test
  void dueLeaseReceiptAndRetentionQueriesHaveTheirPurposeBuiltIndexes() {
    assertEquals(
        "ix_push_delivery_due",
        explainKey(
            """
            SELECT id FROM push_deliveries FORCE INDEX (ix_push_delivery_due)
            WHERE state IN ('PENDING','RETRY_WAIT') AND next_attempt_at <= NOW(6)
            ORDER BY next_attempt_at, id LIMIT 50
            """));
    assertEquals(
        "ix_push_delivery_lease",
        explainKey(
            """
            SELECT id FROM push_deliveries FORCE INDEX (ix_push_delivery_lease)
            WHERE state='SENDING' AND lease_until <= NOW(6)
            ORDER BY lease_until, id LIMIT 50
            """));
    assertEquals(
        "ix_push_delivery_receipt",
        explainKey(
            """
            SELECT id FROM push_deliveries FORCE INDEX (ix_push_delivery_receipt)
            WHERE state='TICKET_ACCEPTED' AND receipt_next_at <= NOW(6)
            ORDER BY receipt_next_at, id LIMIT 50
            """));
    assertEquals(
        "ix_push_delivery_retention",
        explainKey(
            """
            SELECT id FROM push_deliveries FORCE INDEX (ix_push_delivery_retention)
            WHERE created_at < NOW(6) ORDER BY created_at, id LIMIT 500
            """));
  }

  @Test
  void retentionRechecksInactiveStateAfterAConcurrentRegistrationWinsTheRowLock()
      throws Exception {
    insertInactiveDevice(102L, NOW.minusSeconds(31L * 86400L));
    var pool = Executors.newSingleThreadExecutor();
    AtomicReference<java.util.concurrent.Future<?>> cleanup = new AtomicReference<>();
    CountDownLatch cleanupStarted = new CountDownLatch(1);
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              ignored -> {
                jdbc.queryForObject(
                    "SELECT id FROM push_devices WHERE id=102 FOR UPDATE", Long.class);
                cleanup.set(
                    pool.submit(
                        () -> {
                          cleanupStarted.countDown();
                          retention.deleteExpired();
                        }));
                try {
                  assertTrue(cleanupStarted.await(5, TimeUnit.SECONDS));
                  Thread.sleep(200);
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(exception);
                }
                assertTrue(!cleanup.get().isDone());
                jdbc.update(
                    """
                    UPDATE push_devices
                    SET status='ACTIVE', session_id='session-new', binding_id='binding-new',
                        push_token='ExponentPushToken[token-new]',
                        push_token_digest=REPEAT('b',64), inactive_at=NULL, revision=revision+1
                    WHERE id=102
                    """);
              });
      cleanup.get().get(10, TimeUnit.SECONDS);

      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT COUNT(*) FROM push_devices WHERE id=102 AND status='ACTIVE'",
              Integer.class));
    } finally {
      pool.shutdownNow();
    }
  }

  private String explainKey(String sql) {
    return jdbc.queryForObject("EXPLAIN " + sql, (rs, row) -> rs.getString("key"));
  }

  private String state(long id) {
    return jdbc.queryForObject("SELECT state FROM push_deliveries WHERE id = ?", String.class, id);
  }

  private void insertUser(long id) {
    jdbc.update(
        """
        INSERT INTO users (
          id, email, normalized_email, password_hash, nickname, email_verified_at,
          service_terms_version, service_terms_agreed_at, privacy_terms_version,
          privacy_terms_agreed_at, status, activated_at, created_at, updated_at
        ) VALUES (?, ?, ?, 'hash', '공부왕', NOW(6), 'v1', NOW(6), 'v1', NOW(6),
          'ACTIVE', NOW(6), NOW(6), NOW(6))
        """,
        id,
        "user" + id + "@example.com",
        "user" + id + "@example.com");
  }

  private void insertActiveDevice(long id, String bindingId, long tokenVersion, String token) {
    jdbc.update(
        """
        INSERT INTO push_devices (
          id, installation_id, installation_key_digest, user_id, session_id, binding_id,
          revision, token_version, platform, provider, push_token, push_token_digest,
          status, created_at, updated_at
        ) VALUES (?, ?, REPEAT('a',64), 42, 'session-42', ?, 1, ?, 'IOS', 'EXPO', ?,
          REPEAT('a',64), 'ACTIVE', NOW(6), NOW(6))
        """,
        id,
        "installation-" + id,
        bindingId,
        tokenVersion,
        token);
  }

  private void insertInactiveDevice(long id, Instant inactiveAt) {
    jdbc.update(
        """
        INSERT INTO push_devices (
          id, installation_id, installation_key_digest, user_id, revision, token_version,
          platform, provider, status, inactive_at, created_at, updated_at
        ) VALUES (?, ?, REPEAT('c',64), 42, 1, 1, 'IOS', 'EXPO', 'REVOKED',
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'), NOW(6), NOW(6))
        """,
        id,
        "installation-" + id,
        epochMicros(inactiveAt));
  }

  private void insertNotification(long id, String publicId) {
    jdbc.update(
        """
        INSERT INTO notifications (
          id, public_id, user_id, payload_version, notification_type, quiz_set_id, material_id,
          target_name, action_type, created_at, updated_at
        ) VALUES (?, ?, 42, 1, 'QUIZ_GENERATION_READY', ?, 'material-1', '자료구조 퀴즈',
          'FOCUS_QUIZ_IN_LIST', NOW(6), NOW(6))
        """,
        id,
        publicId,
        "quiz-" + id);
  }

  private void insertDelivery(
      long id,
      long notificationId,
      String state,
      int attemptCount,
      Instant createdAt,
      Instant expiresAt,
      String ticketId,
      Instant ticketAcceptedAt) {
    jdbc.update(
        """
        INSERT INTO push_deliveries (
          id, notification_id, device_id, user_id, binding_id, token_version, state,
          attempt_count, expires_at, next_attempt_at, ticket_id, ticket_accepted_at,
          receipt_next_at, created_at, updated_at
        ) VALUES (?, ?, 101, 42, 'binding-101', 1, ?, ?,
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'), ?,
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
          TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'))
        """,
        id,
        notificationId,
        state,
        attemptCount,
        epochMicros(expiresAt),
        epochMicros(createdAt),
        ticketId,
        ticketAcceptedAt == null ? null : epochMicros(ticketAcceptedAt),
        ticketAcceptedAt == null ? null : epochMicros(NOW.minusSeconds(1)),
        epochMicros(createdAt),
        epochMicros(createdAt));
  }

  private long epochMicros(Instant value) {
    return value.getEpochSecond() * 1_000_000L + value.getNano() / 1_000L;
  }

  @TestConfiguration
  static class FixedClockConfiguration {
    @Bean
    Clock pushClaimTestClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }
}
