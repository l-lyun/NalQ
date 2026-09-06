package com.openmd.server.push.service;

import com.openmd.server.push.dto.model.PreparedPushDelivery;
import com.openmd.server.push.dto.model.PushDeliveryAttempt;
import com.openmd.server.push.dto.model.PushGatewayResult;
import com.openmd.server.push.dto.model.PushReceiptAttempt;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PushDeliveryWorker {

  private final PushDeliveryTransaction transactions;
  private final PushGateway gateway;
  private final Clock clock;
  private final int batchSize;
  private final Duration leaseDuration;

  public PushDeliveryWorker(
      PushDeliveryTransaction transactions,
      PushGateway gateway,
      Clock clock,
      int batchSize,
      Duration leaseDuration) {
    this.transactions = transactions;
    this.gateway = gateway;
    this.clock = clock;
    this.batchSize = batchSize;
    this.leaseDuration = leaseDuration;
  }

  public void sendDue() {
    Instant now = clock.instant();
    List<PushDeliveryAttempt> claims = transactions.claimSend(now, batchSize, leaseDuration);
    if (claims.isEmpty()) {
      return;
    }
    List<PreparedPushDelivery> prepared = new ArrayList<>();
    for (PushDeliveryAttempt claim : claims) {
      transactions.prepareSend(claim, clock.instant()).ifPresent(prepared::add);
    }
    if (prepared.isEmpty()) {
      return;
    }
    List<PushGatewayResult> results =
        gateway.sendBatch(prepared.stream().map(PreparedPushDelivery::message).toList());
    for (int index = 0; index < prepared.size(); index++) {
      PushGatewayResult result =
          index < results.size()
              ? results.get(index)
              : PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID");
      transactions.recordSendResult(prepared.get(index).attempt(), result, clock.instant());
    }
  }

  public void checkReceipts() {
    Instant now = clock.instant();
    List<PushReceiptAttempt> claims =
        transactions.claimReceipts(now, batchSize, leaseDuration);
    if (claims.isEmpty()) {
      return;
    }
    Map<String, PushGatewayResult> results =
        gateway.getReceipts(claims.stream().map(PushReceiptAttempt::ticketId).toList());
    for (PushReceiptAttempt claim : claims) {
      transactions.recordReceiptResult(
          claim,
          results.getOrDefault(
              claim.ticketId(), PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID")),
          clock.instant());
    }
  }
}
