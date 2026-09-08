package com.openmd.server.push.dto.request;

import com.openmd.server.push.domain.PushPermission;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.dto.command.RegisterPushDeviceCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record PushDeviceRegistrationRequest(
    @NotBlank String operationId,
    @NotNull Instant operationIssuedAt,
    @PositiveOrZero long expectedRevision,
    @NotNull PushPlatform platform,
    @NotNull PushProvider provider,
    @Size(max = 512) String pushToken,
    @NotNull PushPermission permission) {

  public RegisterPushDeviceCommand toCommand(String installationId, String installationKey) {
    return new RegisterPushDeviceCommand(
        installationId,
        installationKey,
        operationId,
        operationIssuedAt,
        expectedRevision,
        platform,
        provider,
        pushToken,
        permission);
  }

  @Override
  public String toString() {
    return "PushDeviceRegistrationRequest[operationId="
        + operationId
        + ", operationIssuedAt="
        + operationIssuedAt
        + ", expectedRevision="
        + expectedRevision
        + ", platform="
        + platform
        + ", provider="
        + provider
        + ", pushToken=<redacted>, permission="
        + permission
        + "]";
  }
}
