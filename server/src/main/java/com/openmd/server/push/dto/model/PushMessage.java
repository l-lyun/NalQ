package com.openmd.server.push.dto.model;

import java.time.Instant;

/** In-memory provider command; never persist or log its token or personal title. */
public record PushMessage(
    String token, String title, String body, String notificationId, String bindingId,
    Instant expiresAt) {
  @Override
  public String toString() {
    return "PushMessage[redacted]";
  }
}
