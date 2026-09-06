package com.openmd.server.push.repository;

import com.openmd.server.push.dto.model.PreparedPushDelivery;
import com.openmd.server.push.dto.model.PushDeliveryAttempt;
import com.openmd.server.push.dto.model.PushMessage;
import com.openmd.server.push.dto.model.PushReceiptAttempt;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PushDeliveryClaimStore {

  private final JdbcTemplate jdbc;

  public PushDeliveryClaimStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<PushDeliveryAttempt> claimSend(
      Instant now, int limit, Duration leaseDuration) {
    closeUnclaimableSendRows(now, limit);
    List<Long> ids =
        jdbc.queryForList(
            """
            SELECT id
            FROM push_deliveries
            WHERE state IN ('PENDING', 'RETRY_WAIT')
              AND next_attempt_at <= TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
              AND expires_at > TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
              AND attempt_count < 8
            ORDER BY next_attempt_at, id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """,
            Long.class,
            epochMicros(now),
            epochMicros(now),
            limit);
    List<PushDeliveryAttempt> claims = new ArrayList<>(ids.size());
    for (Long id : ids) {
      String attemptId = UUID.randomUUID().toString();
      int updated =
          jdbc.update(
              """
              UPDATE push_deliveries
              SET state = 'SENDING', attempt_id = ?, attempt_count = attempt_count + 1,
                  lease_until = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
                  updated_at = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
              WHERE id = ? AND state IN ('PENDING', 'RETRY_WAIT')
              """,
              attemptId,
              epochMicros(now.plus(leaseDuration)),
              epochMicros(now),
              id);
      if (updated == 1) {
        claims.add(new PushDeliveryAttempt(id, attemptId));
      }
    }
    return List.copyOf(claims);
  }

  public List<PushReceiptAttempt> claimReceipts(
      Instant now, int limit, Duration leaseDuration) {
    closeExpiredReceiptRows(now, limit);
    List<ReceiptCandidate> candidates =
        jdbc.query(
            """
            SELECT id, ticket_id
            FROM push_deliveries
            WHERE state = 'TICKET_ACCEPTED'
              AND receipt_next_at <= TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
              AND ticket_accepted_at > TIMESTAMPADD(
                MICROSECOND, ?, '1970-01-01 00:00:00.000000'
              )
            ORDER BY receipt_next_at, id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """,
            (rs, row) -> new ReceiptCandidate(rs.getLong("id"), rs.getString("ticket_id")),
            epochMicros(now),
            epochMicros(now.minus(Duration.ofHours(24))),
            limit);
    List<PushReceiptAttempt> claims = new ArrayList<>(candidates.size());
    for (ReceiptCandidate candidate : candidates) {
      String attemptId = UUID.randomUUID().toString();
      int updated =
          jdbc.update(
              """
              UPDATE push_deliveries
              SET state = 'RECEIPT_CHECKING', attempt_id = ?,
                  lease_until = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
                  updated_at = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
              WHERE id = ? AND state = 'TICKET_ACCEPTED'
              """,
              attemptId,
              epochMicros(now.plus(leaseDuration)),
              epochMicros(now),
              candidate.id());
      if (updated == 1) {
        claims.add(new PushReceiptAttempt(candidate.id(), attemptId, candidate.ticketId()));
      }
    }
    return List.copyOf(claims);
  }

  public Optional<PreparedPushDelivery> prepareSend(
      PushDeliveryAttempt attempt, Instant now) {
    return jdbc.query(
            """
            SELECT d.id, d.state, d.attempt_id, d.binding_id, d.token_version,
                   TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00.000000', d.expires_at)
                     AS expires_at_micros,
                   TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00.000000', d.lease_until)
                     AS lease_until_micros,
                   pd.status AS device_status, pd.binding_id AS device_binding_id,
                   pd.token_version AS device_token_version, pd.push_token,
                   n.public_id, n.target_name, n.notification_type,
                   u.status AS user_status
            FROM push_deliveries d
            JOIN push_devices pd ON pd.id = d.device_id
            JOIN notifications n ON n.id = d.notification_id
            JOIN users u ON u.id = d.user_id
            WHERE d.id = ?
            FOR UPDATE OF d
            """,
            (rs, row) ->
                new PreparedRow(
                    rs.getLong("id"),
                    rs.getString("state"),
                    rs.getString("attempt_id"),
                    rs.getString("binding_id"),
                    rs.getLong("token_version"),
                    instant(rs.getLong("expires_at_micros")),
                    instant(rs.getLong("lease_until_micros")),
                    rs.getString("device_status"),
                    rs.getString("device_binding_id"),
                    rs.getLong("device_token_version"),
                    rs.getString("push_token"),
                    rs.getString("public_id"),
                    rs.getString("target_name"),
                    rs.getString("notification_type"),
                    rs.getString("user_status")),
            attempt.deliveryId())
        .stream()
        .findFirst()
        .flatMap(row -> prepare(attempt, row, now));
  }

  public Optional<SendFence> lockSendFence(PushDeliveryAttempt attempt) {
    return jdbc.query(
            """
            SELECT attempt_count,
                   TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00.000000', expires_at)
                     AS expires_at_micros,
                   device_id, binding_id, token_version
            FROM push_deliveries
            WHERE id = ? AND state = 'SENDING' AND attempt_id = ?
            FOR UPDATE
            """,
            (rs, row) ->
                new SendFence(
                    rs.getInt("attempt_count"),
                    instant(rs.getLong("expires_at_micros")),
                    rs.getLong("device_id"),
                    rs.getString("binding_id"),
                    rs.getLong("token_version")),
            attempt.deliveryId(),
            attempt.attemptId())
        .stream()
        .findFirst();
  }

  public Optional<ReceiptFence> lockReceiptFence(PushReceiptAttempt attempt) {
    return jdbc.query(
            """
            SELECT TIMESTAMPDIFF(
                     MICROSECOND, '1970-01-01 00:00:00.000000', ticket_accepted_at
                   ) AS ticket_accepted_at_micros,
                   device_id, binding_id, token_version
            FROM push_deliveries
            WHERE id = ? AND state = 'RECEIPT_CHECKING' AND attempt_id = ? AND ticket_id = ?
            FOR UPDATE
            """,
            (rs, row) ->
                new ReceiptFence(
                    instant(rs.getLong("ticket_accepted_at_micros")),
                    rs.getLong("device_id"),
                    rs.getString("binding_id"),
                    rs.getLong("token_version")),
            attempt.deliveryId(),
            attempt.attemptId(),
            attempt.ticketId())
        .stream()
        .findFirst();
  }

  public int updateSend(
      PushDeliveryAttempt attempt,
      String state,
      Instant nextAttemptAt,
      String ticketId,
      Instant ticketAcceptedAt,
      Instant receiptNextAt,
      String errorCode,
      Instant now) {
    return jdbc.update(
        """
        UPDATE push_deliveries
        SET state = ?, attempt_id = NULL, lease_until = NULL,
            next_attempt_at = COALESCE(
              TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'), next_attempt_at
            ), ticket_id = ?,
            ticket_accepted_at = TIMESTAMPADD(
              MICROSECOND, ?, '1970-01-01 00:00:00.000000'
            ),
            receipt_next_at = TIMESTAMPADD(
              MICROSECOND, ?, '1970-01-01 00:00:00.000000'
            ),
            last_error_code = ?,
            updated_at = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
        WHERE id = ? AND state = 'SENDING' AND attempt_id = ?
        """,
        state,
        nullableEpochMicros(nextAttemptAt),
        ticketId,
        nullableEpochMicros(ticketAcceptedAt),
        nullableEpochMicros(receiptNextAt),
        errorCode,
        epochMicros(now),
        attempt.deliveryId(),
        attempt.attemptId());
  }

  public int updateReceipt(
      PushReceiptAttempt attempt,
      String state,
      Instant receiptNextAt,
      String errorCode,
      Instant now) {
    return jdbc.update(
        """
        UPDATE push_deliveries
        SET state = ?, attempt_id = NULL, lease_until = NULL,
            receipt_next_at = TIMESTAMPADD(
              MICROSECOND, ?, '1970-01-01 00:00:00.000000'
            ), last_error_code = ?,
            updated_at = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
        WHERE id = ? AND state = 'RECEIPT_CHECKING' AND attempt_id = ? AND ticket_id = ?
        """,
        state,
        nullableEpochMicros(receiptNextAt),
        errorCode,
        epochMicros(now),
        attempt.deliveryId(),
        attempt.attemptId(),
        attempt.ticketId());
  }

  public int deactivateMatchingDevice(
      long deviceId, String bindingId, long tokenVersion, Instant now) {
    return jdbc.update(
        """
        UPDATE push_devices
        SET status = 'REVOKED', session_id = NULL, binding_id = NULL,
            push_token = NULL, push_token_digest = NULL,
            inactive_at = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000'),
            revision = revision + 1,
            updated_at = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
        WHERE id = ? AND status = 'ACTIVE' AND binding_id = ? AND token_version = ?
        """,
        epochMicros(now),
        epochMicros(now),
        deviceId,
        bindingId,
        tokenVersion);
  }

  public int recoverExpiredLeases(Instant now, int limit) {
    List<LeaseCandidate> candidates =
        jdbc.query(
            """
            SELECT id, state, attempt_count,
                   TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00.000000', expires_at)
                     AS expires_at_micros,
                   TIMESTAMPDIFF(MICROSECOND, '1970-01-01 00:00:00.000000', ticket_accepted_at)
                     AS ticket_accepted_at_micros
            FROM push_deliveries
            WHERE state IN ('SENDING', 'RECEIPT_CHECKING')
              AND lease_until <= TIMESTAMPADD(
                MICROSECOND, ?, '1970-01-01 00:00:00.000000'
              )
            ORDER BY lease_until, id
            LIMIT ?
            FOR UPDATE SKIP LOCKED
            """,
            (rs, row) ->
                new LeaseCandidate(
                    rs.getLong("id"),
                    rs.getString("state"),
                    rs.getInt("attempt_count"),
                    instant(rs.getLong("expires_at_micros")),
                    rs.getObject("ticket_accepted_at_micros") == null
                        ? null
                        : instant(rs.getLong("ticket_accepted_at_micros"))),
            epochMicros(now),
            limit);
    for (LeaseCandidate candidate : candidates) {
      boolean send = "SENDING".equals(candidate.state());
      String nextState =
          send
              ? (candidate.attemptCount() >= 8
                  ? "FAILED"
                  : (!now.isBefore(candidate.expiresAt()) ? "EXPIRED" : "RETRY_WAIT"))
              : (candidate.ticketAcceptedAt() == null
                      || !now.isBefore(candidate.ticketAcceptedAt().plus(Duration.ofHours(24)))
                  ? "UNKNOWN"
                  : "TICKET_ACCEPTED");
      jdbc.update(
          """
          UPDATE push_deliveries
          SET state = ?, next_attempt_at = CASE WHEN ? = 'RETRY_WAIT'
                THEN TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
                ELSE next_attempt_at END,
              receipt_next_at = CASE WHEN ? = 'TICKET_ACCEPTED'
                THEN TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
                ELSE receipt_next_at END,
              attempt_id = NULL, lease_until = NULL, last_error_code = 'LEASE_EXPIRED',
              updated_at = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
          WHERE id = ? AND state = ?
          """,
          nextState,
          nextState,
          epochMicros(now),
          nextState,
          epochMicros(now),
          epochMicros(now),
          candidate.id(),
          candidate.state());
    }
    return candidates.size();
  }

  private Optional<PreparedPushDelivery> prepare(
      PushDeliveryAttempt attempt, PreparedRow row, Instant now) {
    if (!"SENDING".equals(row.state()) || !attempt.attemptId().equals(row.attemptId())) {
      return Optional.empty();
    }
    if (!now.isBefore(row.leaseUntil())) {
      return Optional.empty();
    }
    if (!now.isBefore(row.expiresAt())) {
      updateSend(attempt, "EXPIRED", null, null, null, null, "EXPIRED", now);
      return Optional.empty();
    }
    boolean current =
        "ACTIVE".equals(row.deviceStatus())
            && "ACTIVE".equals(row.userStatus())
            && row.bindingId().equals(row.deviceBindingId())
            && row.tokenVersion() == row.deviceTokenVersion()
            && row.pushToken() != null;
    if (!current) {
      updateSend(attempt, "CANCELLED", null, null, null, null, "BINDING_CHANGED", now);
      return Optional.empty();
    }
    String body =
        "QUIZ_GENERATION_READY".equals(row.notificationType())
            ? "퀴즈가 완성됐어요."
            : "퀴즈를 만들지 못했어요. 앱에서 확인해 주세요.";
    PushMessage message =
        new PushMessage(
            row.pushToken(),
            row.targetName(),
            body,
            row.publicId(),
            row.bindingId(),
            row.expiresAt());
    return Optional.of(new PreparedPushDelivery(attempt, message));
  }

  private long epochMicros(Instant value) {
    return Math.addExact(Math.multiplyExact(value.getEpochSecond(), 1_000_000L), value.getNano() / 1_000L);
  }

  private Long nullableEpochMicros(Instant value) {
    return value == null ? null : epochMicros(value);
  }

  private Instant instant(long epochMicros) {
    return Instant.ofEpochSecond(
        Math.floorDiv(epochMicros, 1_000_000L), Math.floorMod(epochMicros, 1_000_000L) * 1_000L);
  }

  public record SendFence(
      int attemptCount, Instant expiresAt, long deviceId, String bindingId, long tokenVersion) {}

  public record ReceiptFence(
      Instant ticketAcceptedAt, long deviceId, String bindingId, long tokenVersion) {}

  private record ReceiptCandidate(long id, String ticketId) {}

  private record PreparedRow(
      long id,
      String state,
      String attemptId,
      String bindingId,
      long tokenVersion,
      Instant expiresAt,
      Instant leaseUntil,
      String deviceStatus,
      String deviceBindingId,
      long deviceTokenVersion,
      String pushToken,
      String publicId,
      String targetName,
      String notificationType,
      String userStatus) {}

  private record LeaseCandidate(
      long id, String state, int attemptCount, Instant expiresAt, Instant ticketAcceptedAt) {}

  private void closeUnclaimableSendRows(Instant now, int limit) {
    closeSendRows(now, limit, "expires_at <=", "EXPIRED", "EXPIRED");
    closeSendRows(now, limit, "attempt_count >=", "FAILED", "ATTEMPTS_EXHAUSTED");
  }

  private void closeSendRows(
      Instant now, int limit, String condition, String state, String errorCode) {
    String comparison =
        "expires_at <=".equals(condition)
            ? "expires_at <= TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')"
            : "attempt_count >= 8";
    String sql =
        "UPDATE push_deliveries SET state = ?, last_error_code = ?, "
            + "updated_at = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000') "
            + "WHERE id IN (SELECT id FROM (SELECT id FROM push_deliveries "
            + "WHERE state IN ('PENDING', 'RETRY_WAIT') AND "
            + comparison
            + " ORDER BY next_attempt_at, id LIMIT ?) stale_send_rows)";
    if ("expires_at <=".equals(condition)) {
      jdbc.update(sql, state, errorCode, epochMicros(now), epochMicros(now), limit);
    } else {
      jdbc.update(sql, state, errorCode, epochMicros(now), limit);
    }
  }

  private void closeExpiredReceiptRows(Instant now, int limit) {
    jdbc.update(
        """
        UPDATE push_deliveries
        SET state = 'UNKNOWN', last_error_code = 'RECEIPT_EXPIRED',
            updated_at = TIMESTAMPADD(MICROSECOND, ?, '1970-01-01 00:00:00.000000')
        WHERE id IN (
          SELECT id FROM (
            SELECT id FROM push_deliveries
            WHERE state = 'TICKET_ACCEPTED'
              AND ticket_accepted_at <= TIMESTAMPADD(
                MICROSECOND, ?, '1970-01-01 00:00:00.000000'
              )
            ORDER BY ticket_accepted_at, id
            LIMIT ?
          ) stale_receipt_rows
        )
        """,
        epochMicros(now),
        epochMicros(now.minus(Duration.ofHours(24))),
        limit);
  }
}
