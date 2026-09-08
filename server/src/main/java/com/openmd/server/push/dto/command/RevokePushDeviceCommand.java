package com.openmd.server.push.dto.command;

import java.time.Instant;

public record RevokePushDeviceCommand(
    String installationId,
    String installationKey,
    String operationId,
    Instant operationIssuedAt,
    String bindingId,
    long expectedRevision) {

  @Override
  public String toString() {
    return "RevokePushDeviceCommand[installationId="
        + installationId
        + ", installationKey=[REDACTED], operationId="
        + operationId
        + ", operationIssuedAt="
        + operationIssuedAt
        + ", bindingId="
        + bindingId
        + ", expectedRevision="
        + expectedRevision
        + "]";
  }
}
