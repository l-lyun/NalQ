package com.openmd.server.push.service;

import com.openmd.server.push.dto.model.PushGatewayResult;
import com.openmd.server.push.dto.model.PushMessage;
import java.util.List;
import java.util.Map;

public interface PushGateway {
  /** One result per message, preserving order. This boundary never retries a send. */
  List<PushGatewayResult> sendBatch(List<PushMessage> messages);

  /** Includes each requested ticket; a missing receipt is PENDING, never a resend request. */
  Map<String, PushGatewayResult> getReceipts(List<String> ticketIds);
}
