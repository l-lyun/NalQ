package com.openmd.server.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.push.dto.model.PreparedPushDelivery;
import com.openmd.server.push.dto.model.PushDeliveryAttempt;
import com.openmd.server.push.dto.model.PushGatewayResult;
import com.openmd.server.push.dto.model.PushMessage;
import com.openmd.server.push.dto.model.PushReceiptAttempt;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PushDeliveryWorkerTest {

  private static final Instant NOW = Instant.parse("2026-09-06T06:00:00Z");
  private final PushDeliveryTransaction transactions =
      org.mockito.Mockito.mock(PushDeliveryTransaction.class);
  private final AtomicBoolean gatewayObservedTransaction = new AtomicBoolean(true);
  private final AtomicReference<List<PushMessage>> sentMessages =
      new AtomicReference<>(List.of());
  private final PushGateway gateway =
      new PushGateway() {
        @Override
        public List<PushGatewayResult> sendBatch(List<PushMessage> messages) {
          gatewayObservedTransaction.set(
              TransactionSynchronizationManager.isActualTransactionActive());
          sentMessages.set(List.copyOf(messages));
          return messages.stream().map(ignored -> PushGatewayResult.accepted("ticket-1")).toList();
        }

        @Override
        public Map<String, PushGatewayResult> getReceipts(List<String> ticketIds) {
          gatewayObservedTransaction.set(
              TransactionSynchronizationManager.isActualTransactionActive());
          return Map.of("ticket-1", PushGatewayResult.accepted(null));
        }
      };
  private PushDeliveryWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new PushDeliveryWorker(
            transactions, gateway, Clock.fixed(NOW, ZoneOffset.UTC), 50, Duration.ofSeconds(60));
  }

  @Test
  void sendsOnlyPreparedClaimsAndCallsTheProviderOutsideADatabaseTransaction() {
    PushDeliveryAttempt first = new PushDeliveryAttempt(1L, "attempt-1");
    PushDeliveryAttempt cancelled = new PushDeliveryAttempt(2L, "attempt-2");
    PushMessage message =
        new PushMessage(
            "ExponentPushToken[token]",
            "자료구조 퀴즈",
            "퀴즈가 완성됐어요.",
            "notification-1",
            "binding-1",
            NOW.plusSeconds(3600));
    when(transactions.claimSend(NOW, 50, Duration.ofSeconds(60)))
        .thenReturn(List.of(first, cancelled));
    when(transactions.prepareSend(first, NOW))
        .thenReturn(Optional.of(new PreparedPushDelivery(first, message)));
    when(transactions.prepareSend(cancelled, NOW)).thenReturn(Optional.empty());
    when(transactions.renewSendLeases(List.of(first), NOW, Duration.ofSeconds(60)))
        .thenReturn(List.of(first));

    worker.sendDue();

    assertFalse(gatewayObservedTransaction.get());
    verify(transactions)
        .recordSendResult(first, PushGatewayResult.accepted("ticket-1"), NOW);
  }

  @Test
  void sendsOnlyClaimsWhoseLeaseIsRenewedImmediatelyBeforeTheProviderCall() {
    PushDeliveryAttempt stale = new PushDeliveryAttempt(1L, "attempt-stale");
    PushDeliveryAttempt current = new PushDeliveryAttempt(2L, "attempt-current");
    PushMessage staleMessage =
        new PushMessage(
            "ExponentPushToken[stale]",
            "오래된 claim",
            "퀴즈가 완성됐어요.",
            "notification-stale",
            "binding-stale",
            NOW.plusSeconds(3600));
    PushMessage currentMessage =
        new PushMessage(
            "ExponentPushToken[current]",
            "유효한 claim",
            "퀴즈가 완성됐어요.",
            "notification-current",
            "binding-current",
            NOW.plusSeconds(3600));
    when(transactions.claimSend(NOW, 50, Duration.ofSeconds(60)))
        .thenReturn(List.of(stale, current));
    when(transactions.prepareSend(stale, NOW))
        .thenReturn(Optional.of(new PreparedPushDelivery(stale, staleMessage)));
    when(transactions.prepareSend(current, NOW))
        .thenReturn(Optional.of(new PreparedPushDelivery(current, currentMessage)));
    when(transactions.renewSendLeases(
            List.of(stale, current), NOW, Duration.ofSeconds(60)))
        .thenReturn(List.of(current));

    worker.sendDue();

    assertEquals(List.of(currentMessage), sentMessages.get());
    verify(transactions)
        .recordSendResult(current, PushGatewayResult.accepted("ticket-1"), NOW);
  }

  @Test
  void checksReceiptsWithoutTurningPendingReceiptsIntoNewSends() {
    PushReceiptAttempt receipt = new PushReceiptAttempt(1L, "receipt-attempt", "ticket-1");
    when(transactions.claimReceipts(NOW, 50, Duration.ofSeconds(60)))
        .thenReturn(List.of(receipt));

    worker.checkReceipts();

    assertFalse(gatewayObservedTransaction.get());
    verify(transactions)
        .recordReceiptResult(receipt, PushGatewayResult.accepted(null), NOW);
  }

  @Test
  void emptyClaimsNeverCallTheProvider() {
    when(transactions.claimSend(NOW, 50, Duration.ofSeconds(60))).thenReturn(List.of());
    gatewayObservedTransaction.set(true);

    worker.sendDue();

    assertTrue(gatewayObservedTransaction.get());
  }
}
