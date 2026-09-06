package com.openmd.server.push.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PushDeliveryWorkerTest {

  private static final Instant NOW = Instant.parse("2026-09-06T06:00:00Z");
  private final PushDeliveryTransaction transactions =
      org.mockito.Mockito.mock(PushDeliveryTransaction.class);
  private final AtomicBoolean gatewayObservedTransaction = new AtomicBoolean(true);
  private final PushGateway gateway =
      new PushGateway() {
        @Override
        public List<PushGatewayResult> sendBatch(List<PushMessage> messages) {
          gatewayObservedTransaction.set(
              TransactionSynchronizationManager.isActualTransactionActive());
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

    worker.sendDue();

    assertFalse(gatewayObservedTransaction.get());
    verify(transactions)
        .recordSendResult(first, PushGatewayResult.accepted("ticket-1"), NOW);
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
