package com.openmd.server.push.service;

import com.openmd.server.push.dto.model.PreparedPushDelivery;
import com.openmd.server.push.dto.model.PushDeliveryAttempt;
import com.openmd.server.push.dto.model.PushGatewayResult;
import com.openmd.server.push.dto.model.PushReceiptAttempt;
import com.openmd.server.push.repository.PushDeliveryClaimStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class PushDeliveryTransaction {

  private final PushDeliveryClaimStore store;
  private final PushDeliveryPolicy policy;

  public PushDeliveryTransaction(PushDeliveryClaimStore store, PushDeliveryPolicy policy) {
    this.store = store;
    this.policy = policy;
  }

  @Transactional
  public List<PushDeliveryAttempt> claimSend(
      Instant now, int limit, Duration leaseDuration) {
    return store.claimSend(now, limit, leaseDuration);
  }

  @Transactional
  public Optional<PreparedPushDelivery> prepareSend(
      PushDeliveryAttempt attempt, Instant now) {
    return store.prepareSend(attempt, now);
  }

  @Transactional
  public void recordSendResult(
      PushDeliveryAttempt attempt, PushGatewayResult result, Instant now) {
    Optional<PushDeliveryClaimStore.SendFence> fence = store.lockSendFence(attempt);
    if (fence.isEmpty()) {
      return;
    }
    var current = fence.get();
    switch (result.outcome()) {
      case ACCEPTED -> {
        if (result.ticketId() == null || result.ticketId().isBlank()) {
          retrySend(attempt, current, result.retryAfter(), "PROVIDER_RESPONSE_INVALID", now);
        } else {
          store.updateSend(
              attempt,
              "TICKET_ACCEPTED",
              null,
              result.ticketId(),
              now,
              policy.firstReceiptAt(now),
              null,
              now);
        }
      }
      case RETRY, PENDING ->
          retrySend(attempt, current, result.retryAfter(), result.errorCode(), now);
      case INVALID_TOKEN -> {
        store.updateSend(attempt, "FAILED", null, null, null, null, result.errorCode(), now);
        store.deactivateMatchingDevice(
            current.deviceId(), current.bindingId(), current.tokenVersion(), now);
      }
      case FAILED ->
          store.updateSend(
              attempt,
              "EXPIRED".equals(result.errorCode()) ? "EXPIRED" : "FAILED",
              null,
              null,
              null,
              null,
              result.errorCode(),
              now);
    }
  }

  @Transactional
  public List<PushReceiptAttempt> claimReceipts(
      Instant now, int limit, Duration leaseDuration) {
    return store.claimReceipts(now, limit, leaseDuration);
  }

  @Transactional
  public void recordReceiptResult(
      PushReceiptAttempt attempt, PushGatewayResult result, Instant now) {
    Optional<PushDeliveryClaimStore.ReceiptFence> fence = store.lockReceiptFence(attempt);
    if (fence.isEmpty()) {
      return;
    }
    var current = fence.get();
    switch (result.outcome()) {
      case ACCEPTED -> store.updateReceipt(attempt, "PROVIDER_ACCEPTED", null, null, now);
      case PENDING, RETRY -> {
        Optional<Instant> next = policy.nextReceiptCheck(now, current.ticketAcceptedAt());
        store.updateReceipt(
            attempt,
            next.isPresent() ? "TICKET_ACCEPTED" : "UNKNOWN",
            next.orElse(null),
            result.errorCode(),
            now);
      }
      case INVALID_TOKEN -> {
        store.updateReceipt(attempt, "FAILED", null, result.errorCode(), now);
        store.deactivateMatchingDevice(
            current.deviceId(), current.bindingId(), current.tokenVersion(), now);
      }
      case FAILED -> store.updateReceipt(attempt, "FAILED", null, result.errorCode(), now);
    }
  }

  @Transactional
  public int recoverExpiredLeases(Instant now, int limit) {
    return store.recoverExpiredLeases(now, limit);
  }

  private void retrySend(
      PushDeliveryAttempt attempt,
      PushDeliveryClaimStore.SendFence fence,
      Duration retryAfter,
      String errorCode,
      Instant now) {
    if (fence.attemptCount() >= PushDeliveryPolicy.MAX_SEND_ATTEMPTS) {
      store.updateSend(
          attempt,
          "FAILED",
          null,
          null,
          null,
          null,
          errorCode == null ? "ATTEMPTS_EXHAUSTED" : errorCode,
          now);
      return;
    }
    Optional<Instant> next =
        policy.nextSendRetry(now, fence.attemptCount(), retryAfter, fence.expiresAt());
    store.updateSend(
        attempt,
        next.isPresent() ? "RETRY_WAIT" : "EXPIRED",
        next.orElse(null),
        null,
        null,
        null,
        errorCode,
        now);
  }
}
