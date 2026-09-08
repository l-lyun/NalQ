package com.openmd.server.push.dto.model;

import java.time.Duration;

public record PushGatewayResult(
    Outcome outcome, String ticketId, String errorCode, Duration retryAfter) {
  public enum Outcome { ACCEPTED, RETRY, INVALID_TOKEN, FAILED, PENDING }

  public PushGatewayResult {
    if (retryAfter == null || retryAfter.isNegative()) retryAfter = Duration.ZERO;
  }

  public static PushGatewayResult accepted(String ticketId) {
    return new PushGatewayResult(Outcome.ACCEPTED, ticketId, null, Duration.ZERO);
  }

  public static PushGatewayResult retry(String code) {
    return new PushGatewayResult(Outcome.RETRY, null, code, Duration.ZERO);
  }
}
