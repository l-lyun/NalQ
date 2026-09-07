package com.openmd.server.push.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "push_deliveries")
public class PushDelivery extends BaseEntity {
  @Column(name = "notification_id", nullable = false, updatable = false)
  private long notificationId;
  @Column(name = "device_id", nullable = false, updatable = false)
  private long deviceId;
  @Column(name = "user_id", nullable = false, updatable = false)
  private long userId;
  @Column(name = "binding_id", nullable = false, updatable = false, length = 36)
  private String bindingId;
  @Column(name = "token_version", nullable = false, updatable = false)
  private long tokenVersion;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PushDeliveryState state;
  @Column(name = "attempt_id", length = 36)
  private String attemptId;
  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;
  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;
  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;
  @Column(name = "lease_until")
  private Instant leaseUntil;
  @Column(name = "ticket_id", length = 255)
  private String ticketId;
  @Column(name = "ticket_accepted_at")
  private Instant ticketAcceptedAt;
  @Column(name = "receipt_next_at")
  private Instant receiptNextAt;
  @Column(name = "last_error_code", length = 64)
  private String lastErrorCode;

  protected PushDelivery() {}

  public static PushDelivery pending(long notificationId, long userId, PushDevice device,
      Instant notificationCreatedAt) {
    var delivery = new PushDelivery();
    delivery.notificationId = notificationId;
    delivery.userId = userId;
    delivery.deviceId = device.getId();
    delivery.bindingId = device.getBindingId();
    delivery.tokenVersion = device.getTokenVersion();
    delivery.state = PushDeliveryState.PENDING;
    delivery.expiresAt = notificationCreatedAt.plusSeconds(3600);
    delivery.nextAttemptAt = notificationCreatedAt;
    return delivery;
  }

  public long getNotificationId() { return notificationId; }
  public long getDeviceId() { return deviceId; }
  public long getUserId() { return userId; }
  public String getBindingId() { return bindingId; }
  public long getTokenVersion() { return tokenVersion; }
  public PushDeliveryState getState() { return state; }
  public String getAttemptId() { return attemptId; }
  public int getAttemptCount() { return attemptCount; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
  public Instant getLeaseUntil() { return leaseUntil; }
  public String getTicketId() { return ticketId; }
  public Instant getTicketAcceptedAt() { return ticketAcceptedAt; }
  public Instant getReceiptNextAt() { return receiptNextAt; }
  public String getLastErrorCode() { return lastErrorCode; }
}
