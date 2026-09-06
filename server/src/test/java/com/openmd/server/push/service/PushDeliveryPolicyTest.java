package com.openmd.server.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PushDeliveryPolicyTest {

  private static final Instant NOW = Instant.parse("2026-09-06T06:00:00Z");

  @Test
  void sendRetryUsesBoundedExponentialBackoffJitterAndProviderRetryAfter() {
    PushDeliveryPolicy noJitter = new PushDeliveryPolicy(() -> 0.0);
    PushDeliveryPolicy halfJitter = new PushDeliveryPolicy(() -> 0.5);

    assertEquals(
        NOW.plusSeconds(30),
        noJitter.nextSendRetry(NOW, 1, Duration.ZERO, NOW.plusSeconds(3600)).orElseThrow());
    assertEquals(
        NOW.plusSeconds(33),
        halfJitter.nextSendRetry(NOW, 1, Duration.ZERO, NOW.plusSeconds(3600)).orElseThrow());
    assertEquals(
        NOW.plusSeconds(120),
        noJitter
            .nextSendRetry(NOW, 1, Duration.ofSeconds(120), NOW.plusSeconds(3600))
            .orElseThrow());
    assertEquals(
        NOW.plusSeconds(600),
        halfJitter.nextSendRetry(NOW, 8 - 1, Duration.ZERO, NOW.plusSeconds(3600)).orElseThrow());
  }

  @Test
  void sendRetryStopsAtEightAttemptsOrAtTheOriginalExpiry() {
    PushDeliveryPolicy policy = new PushDeliveryPolicy(() -> 0.0);

    assertTrue(policy.nextSendRetry(NOW, 8, Duration.ZERO, NOW.plusSeconds(3600)).isEmpty());
    assertTrue(policy.nextSendRetry(NOW, 1, Duration.ZERO, NOW.plusSeconds(30)).isEmpty());
  }

  @Test
  void receiptPollingStartsAtFifteenMinutesAndEndsAtTheFixedTwentyFourHourDeadline() {
    PushDeliveryPolicy policy = new PushDeliveryPolicy(() -> 0.0);

    assertEquals(NOW.plusSeconds(900), policy.firstReceiptAt(NOW));
    assertEquals(
        NOW.plusSeconds(1200),
        policy.nextReceiptCheck(NOW.plusSeconds(900), NOW).orElseThrow());
    assertEquals(
        NOW.plusSeconds(86400),
        policy.nextReceiptCheck(NOW.plusSeconds(86100), NOW).orElseThrow());
    assertTrue(policy.nextReceiptCheck(NOW.plusSeconds(86400), NOW).isEmpty());
  }
}
