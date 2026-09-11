package com.openmd.server.push.service;

import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.dto.model.PushGatewayResult;
import com.openmd.server.push.dto.model.PushGatewayResult.Outcome;
import com.openmd.server.push.dto.model.PushMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RoutingPushGateway implements PushGateway {
  private final Map<PushProvider, PushGateway> gateways;
  private final PushGateway receiptGateway;

  public RoutingPushGateway(Map<PushProvider, PushGateway> gateways, PushGateway receiptGateway) {
    this.gateways = Map.copyOf(new EnumMap<>(gateways));
    this.receiptGateway = receiptGateway;
  }

  @Override
  public List<PushGatewayResult> sendBatch(List<PushMessage> messages) {
    var results = new ArrayList<PushGatewayResult>(messages.size());
    for (PushMessage message : messages) {
      PushGateway gateway = gateways.get(message.provider());
      if (gateway == null) {
        results.add(new PushGatewayResult(
            Outcome.FAILED, null, "PROVIDER_NOT_CONFIGURED", Duration.ZERO));
      } else {
        List<PushGatewayResult> sent = gateway.sendBatch(List.of(message));
        results.add(sent.size() == 1 ? sent.getFirst()
            : PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID"));
      }
    }
    return List.copyOf(results);
  }

  @Override
  public Map<String, PushGatewayResult> getReceipts(List<String> ticketIds) {
    return receiptGateway.getReceipts(ticketIds);
  }
}
