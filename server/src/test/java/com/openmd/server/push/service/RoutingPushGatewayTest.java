package com.openmd.server.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.dto.model.PushGatewayResult;
import com.openmd.server.push.dto.model.PushMessage;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingPushGatewayTest {

  @Test
  void routesAClaimToTheGatewaySelectedByItsStoredProvider() {
    PushGateway expo = new FixedGateway(PushGatewayResult.accepted("expo-ticket"));
    PushGateway fcm = new FixedGateway(PushGatewayResult.confirmed("fcm-message"));
    var gateways = new EnumMap<PushProvider, PushGateway>(PushProvider.class);
    gateways.put(PushProvider.EXPO, expo);
    gateways.put(PushProvider.FCM, fcm);
    var routing = new RoutingPushGateway(gateways, expo);

    var results = routing.sendBatch(List.of(
        message(PushProvider.EXPO, "ExponentPushToken[test]"),
        message(PushProvider.FCM, "fcm-registration-token-123")));

    assertEquals(PushGatewayResult.Outcome.ACCEPTED, results.get(0).outcome());
    assertEquals(PushGatewayResult.Outcome.CONFIRMED, results.get(1).outcome());
  }

  @Test
  void keepsReceiptChecksOnTheExpoGateway() {
    PushGateway expo = new FixedGateway(PushGatewayResult.accepted("unused"));
    var gateways = new EnumMap<PushProvider, PushGateway>(PushProvider.class);
    gateways.put(PushProvider.EXPO, expo);
    var routing = new RoutingPushGateway(gateways, expo);

    assertEquals(
        PushGatewayResult.Outcome.ACCEPTED,
        routing.getReceipts(List.of("expo-ticket")).get("expo-ticket").outcome());
  }

  private static PushMessage message(PushProvider provider, String token) {
    return new PushMessage(
        provider, token, "title", "body", "notification", "binding", Instant.now().plusSeconds(60));
  }

  private record FixedGateway(PushGatewayResult result) implements PushGateway {
    @Override
    public List<PushGatewayResult> sendBatch(List<PushMessage> messages) {
      return List.of(result);
    }

    @Override
    public Map<String, PushGatewayResult> getReceipts(List<String> ticketIds) {
      return Map.of(ticketIds.getFirst(), result);
    }
  }
}
