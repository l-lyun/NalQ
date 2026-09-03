package com.openmd.server.notification.service;

import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.notification.domain.QuizGenerationNotification;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

record NotificationCursor(Instant createdAt, String notificationId) {
  static NotificationCursor decode(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      String decoded =
          new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
      String[] parts = decoded.split(":", 3);
      if (parts.length != 3) throw new IllegalArgumentException();
      Instant createdAt =
          Instant.ofEpochSecond(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
      String notificationId = UUID.fromString(parts[2]).toString();
      return new NotificationCursor(createdAt, notificationId);
    } catch (RuntimeException exception) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT,
          List.of(new FieldError("cursor", "cursor가 올바르지 않습니다.")));
    }
  }

  static String encode(QuizGenerationNotification notification) {
    Instant createdAt = notification.getCreatedAt();
    String raw =
        createdAt.getEpochSecond()
            + ":"
            + createdAt.getNano()
            + ":"
            + notification.getPublicId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
