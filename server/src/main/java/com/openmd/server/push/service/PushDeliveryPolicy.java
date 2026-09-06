package com.openmd.server.push.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.DoubleSupplier;

public final class PushDeliveryPolicy {

  public static final int MAX_SEND_ATTEMPTS = 8;
  private static final Duration FIRST_RETRY = Duration.ofSeconds(30);
  private static final Duration MAX_RETRY = Duration.ofMinutes(10);
  private static final Duration FIRST_RECEIPT_DELAY = Duration.ofMinutes(15);
  private static final Duration RECEIPT_RETRY = Duration.ofMinutes(5);
  private static final Duration RECEIPT_LIFETIME = Duration.ofHours(24);

  private final DoubleSupplier jitter;

  public PushDeliveryPolicy(DoubleSupplier jitter) {
    this.jitter = jitter;
  }

  public Optional<Instant> nextSendRetry(
      Instant now, int completedAttempts, Duration retryAfter, Instant expiresAt) {
    if (completedAttempts >= MAX_SEND_ATTEMPTS || !now.isBefore(expiresAt)) {
      return Optional.empty();
    }
    long exponent = Math.max(0, Math.min(30, completedAttempts - 1));
    long exponentialSeconds = FIRST_RETRY.toSeconds() * (1L << exponent);
    long baseSeconds = Math.min(MAX_RETRY.toSeconds(), exponentialSeconds);
    double jitterFraction = Math.max(0.0, Math.min(1.0, jitter.getAsDouble()));
    long jitteredSeconds = Math.min(
        MAX_RETRY.toSeconds(), Math.round(baseSeconds * (1.0 + (0.2 * jitterFraction))));
    Duration safeRetryAfter = retryAfter == null || retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    long delaySeconds = Math.max(jitteredSeconds, safeRetryAfter.toSeconds());
    Instant next = now.plusSeconds(delaySeconds);
    return next.isBefore(expiresAt) ? Optional.of(next) : Optional.empty();
  }

  public Instant firstReceiptAt(Instant ticketAcceptedAt) {
    return ticketAcceptedAt.plus(FIRST_RECEIPT_DELAY);
  }

  public Optional<Instant> nextReceiptCheck(Instant now, Instant ticketAcceptedAt) {
    Instant deadline = ticketAcceptedAt.plus(RECEIPT_LIFETIME);
    if (!now.isBefore(deadline)) {
      return Optional.empty();
    }
    Instant next = now.plus(RECEIPT_RETRY);
    return Optional.of(next.isBefore(deadline) ? next : deadline);
  }
}
