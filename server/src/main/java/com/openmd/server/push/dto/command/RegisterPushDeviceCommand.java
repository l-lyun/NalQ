package com.openmd.server.push.dto.command;

import com.openmd.server.push.domain.PushPermission;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.domain.PushProvider;
import java.time.Instant;

public record RegisterPushDeviceCommand(
    String installationId,
    String installationKey,
    String operationId,
    Instant operationIssuedAt,
    long expectedRevision,
    PushPlatform platform,
    PushProvider provider,
    String pushToken,
    PushPermission permission) {

  @Override
  public String toString() {
    return "RegisterPushDeviceCommand[installationId="
        + installationId
        + ", installationKey=[REDACTED], operationId="
        + operationId
        + ", operationIssuedAt="
        + operationIssuedAt
        + ", expectedRevision="
        + expectedRevision
        + ", platform="
        + platform
        + ", provider="
        + provider
        + ", pushToken=[REDACTED], permission="
        + permission
        + "]";
  }
}
