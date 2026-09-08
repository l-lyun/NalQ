package com.openmd.server.push.dto.request;

import com.openmd.server.push.dto.command.RevokePushDeviceCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record PushDeviceRevokeRequest(
    @NotBlank String operationId,
    @NotNull Instant operationIssuedAt,
    @NotBlank String bindingId,
    @PositiveOrZero long expectedRevision) {

  public RevokePushDeviceCommand toCommand(String installationId, String installationKey) {
    return new RevokePushDeviceCommand(
        installationId,
        installationKey,
        operationId,
        operationIssuedAt,
        bindingId,
        expectedRevision);
  }
}
