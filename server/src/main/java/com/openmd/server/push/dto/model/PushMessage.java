package com.openmd.server.push.dto.model;

import com.openmd.server.push.domain.PushProvider;
import java.time.Instant;

/** In-memory provider command; never persist or log its token or personal title. */
public record PushMessage(
    PushProvider provider, String token, String title, String body, String notificationId, String bindingId,
    Instant expiresAt) {
  public PushMessage(
      String token, String title, String body, String notificationId, String bindingId,
      Instant expiresAt) {
    this(PushProvider.EXPO, token, title, body, notificationId, bindingId, expiresAt);
  }

  @Override
  public String toString() {
    return "PushMessage[redacted]";
  }
}
