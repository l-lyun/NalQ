package com.openmd.server.push.domain;

import com.openmd.server.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "push_devices")
public class PushDevice extends BaseEntity {

  @Column(name = "installation_id", nullable = false, updatable = false, length = 36, unique = true)
  private String installationId;

  @Column(name = "installation_key_digest", nullable = false, length = 64)
  private String installationKeyDigest;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "session_id", length = 128)
  private String sessionId;

  @Column(name = "binding_id", length = 36)
  private String bindingId;

  @Column(nullable = false)
  private long revision;

  @Column(name = "token_version", nullable = false)
  private long tokenVersion;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private PushPlatform platform;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private PushProvider provider;

  @Column(name = "push_token", length = 512)
  private String pushToken;

  @Column(name = "push_token_digest", length = 64)
  private String pushTokenDigest;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PushDeviceStatus status;

  @Column(name = "inactive_at")
  private Instant inactiveAt;

  protected PushDevice() {}

  private PushDevice(
      String installationId,
      String installationKeyDigest,
      long userId,
      String sessionId,
      String bindingId,
      PushPlatform platform,
      PushProvider provider,
      String pushToken,
      String pushTokenDigest) {
    this.installationId = installationId;
    this.installationKeyDigest = installationKeyDigest;
    this.userId = userId;
    this.sessionId = sessionId;
    this.bindingId = bindingId;
    this.revision = 1L;
    this.tokenVersion = 1L;
    this.platform = platform;
    this.provider = provider;
    this.pushToken = pushToken;
    this.pushTokenDigest = pushTokenDigest;
    this.status = PushDeviceStatus.ACTIVE;
  }

  public static PushDevice registered(
      String installationId,
      String installationKeyDigest,
      long userId,
      String sessionId,
      String bindingId,
      PushPlatform platform,
      PushProvider provider,
      String pushToken,
      String pushTokenDigest) {
    return new PushDevice(
        installationId,
        installationKeyDigest,
        userId,
        sessionId,
        bindingId,
        platform,
        provider,
        pushToken,
        pushTokenDigest);
  }

  public void register(
      long userId,
      String sessionId,
      String bindingId,
      PushPlatform platform,
      PushProvider provider,
      String pushToken,
      String pushTokenDigest) {
    if (!Objects.equals(this.pushTokenDigest, pushTokenDigest)) {
      tokenVersion++;
    }
    this.userId = userId;
    this.sessionId = sessionId;
    this.bindingId = bindingId;
    this.platform = platform;
    this.provider = provider;
    this.pushToken = pushToken;
    this.pushTokenDigest = pushTokenDigest;
    this.status = PushDeviceStatus.ACTIVE;
    this.inactiveAt = null;
    this.revision++;
  }

  public boolean disable(Instant now) {
    return deactivate(PushDeviceStatus.DISABLED, now);
  }

  public boolean revoke(Instant now) {
    return deactivate(PushDeviceStatus.REVOKED, now);
  }

  private boolean deactivate(PushDeviceStatus newStatus, Instant now) {
    if (status != PushDeviceStatus.ACTIVE) {
      return false;
    }
    this.status = newStatus;
    this.sessionId = null;
    this.bindingId = null;
    this.pushToken = null;
    this.pushTokenDigest = null;
    this.inactiveAt = now;
    this.revision++;
    return true;
  }

  public String getInstallationId() {
    return installationId;
  }

  public String getInstallationKeyDigest() {
    return installationKeyDigest;
  }

  public Long getUserId() {
    return userId;
  }

  public String getSessionId() {
    return sessionId;
  }

  public String getBindingId() {
    return bindingId;
  }

  public long getRevision() {
    return revision;
  }

  public long getTokenVersion() {
    return tokenVersion;
  }

  public PushPlatform getPlatform() {
    return platform;
  }

  public PushProvider getProvider() {
    return provider;
  }

  public String getPushToken() {
    return pushToken;
  }

  public String getPushTokenDigest() {
    return pushTokenDigest;
  }

  public PushDeviceStatus getStatus() {
    return status;
  }

  public Instant getInactiveAt() {
    return inactiveAt;
  }
}
