package com.openmd.server.push.domain;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.push.dto.response.PushDeviceRegistrationResult;
import com.openmd.server.push.dto.response.PushDeviceRevokeResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "push_device_operations")
public class PushDeviceOperation extends BaseEntity {

  @Column(name = "installation_id", nullable = false, updatable = false, length = 36)
  private String installationId;

  @Column(name = "operation_id", nullable = false, updatable = false, length = 36)
  private String operationId;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation_type", nullable = false, updatable = false, length = 16)
  private PushDeviceOperationType operationType;

  @Column(name = "subject_user_id", updatable = false)
  private Long subjectUserId;

  @Column(name = "request_digest", nullable = false, updatable = false, length = 64)
  private String requestDigest;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private Instant issuedAt;

  @Column(name = "result_revision")
  private Long resultRevision;

  @Column(name = "result_binding_id", length = 36)
  private String resultBindingId;

  @Enumerated(EnumType.STRING)
  @Column(name = "result_status", length = 16)
  private PushDeviceStatus resultStatus;

  @Column(name = "result_user_id")
  private Long resultUserId;

  @Column(name = "result_revoked")
  private Boolean resultRevoked;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  protected PushDeviceOperation() {}

  private PushDeviceOperation(
      String installationId,
      String operationId,
      PushDeviceOperationType operationType,
      Long subjectUserId,
      String requestDigest,
      Instant issuedAt,
      Instant processedAt) {
    this.installationId = installationId;
    this.operationId = operationId;
    this.operationType = operationType;
    this.subjectUserId = subjectUserId;
    this.requestDigest = requestDigest;
    this.issuedAt = issuedAt;
    this.expiresAt = processedAt.plusSeconds(7L * 24 * 60 * 60);
  }

  public static PushDeviceOperation successfulRegistration(
      String installationId,
      String operationId,
      long userId,
      String requestDigest,
      Instant issuedAt,
      PushDeviceRegistrationResult result,
      Instant processedAt) {
    PushDeviceOperation operation =
        new PushDeviceOperation(
            installationId,
            operationId,
            PushDeviceOperationType.REGISTER,
            userId,
            requestDigest,
            issuedAt,
            processedAt);
    operation.resultRevision = result.revision();
    operation.resultBindingId = result.bindingId();
    operation.resultStatus = result.status();
    operation.resultUserId = result.userId();
    return operation;
  }

  public static PushDeviceOperation successfulRevoke(
      String installationId,
      String operationId,
      Long userId,
      String requestDigest,
      Instant issuedAt,
      PushDeviceRevokeResult result,
      Instant processedAt) {
    PushDeviceOperation operation =
        new PushDeviceOperation(
            installationId,
            operationId,
            PushDeviceOperationType.REVOKE,
            userId,
            requestDigest,
            issuedAt,
            processedAt);
    operation.resultRevoked = result.revoked();
    return operation;
  }

  public PushDeviceRegistrationResult registrationResult() {
    return new PushDeviceRegistrationResult(
        installationId, resultRevision, resultBindingId, resultStatus, resultUserId);
  }

  public PushDeviceRevokeResult revokeResult() {
    return new PushDeviceRevokeResult(Boolean.TRUE.equals(resultRevoked));
  }

  public boolean matchesRegistration(long userId, String digest) {
    return operationType == PushDeviceOperationType.REGISTER
        && subjectUserId != null
        && subjectUserId == userId
        && requestDigest.equals(digest);
  }

  public boolean matchesRevoke(String digest) {
    return operationType == PushDeviceOperationType.REVOKE && requestDigest.equals(digest);
  }

  public String getRequestDigest() {
    return requestDigest;
  }
}
