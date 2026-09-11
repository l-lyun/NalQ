package com.openmd.server.push.integration.fcm;

import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.dto.model.PushGatewayResult;
import com.openmd.server.push.dto.model.PushGatewayResult.Outcome;
import com.openmd.server.push.dto.model.PushMessage;
import com.openmd.server.push.service.PushGateway;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Sends iOS messages through FCM HTTP v1 without exposing tokens or provider bodies. */
public final class FcmPushGateway implements PushGateway {
  private final HttpClient client;
  private final ObjectMapper mapper;
  private final URI endpoint;
  private final FcmAccessTokenSupplier tokens;
  private final Duration timeout;
  private final Clock clock;

  public FcmPushGateway(HttpClient client, ObjectMapper mapper, URI endpoint,
      FcmAccessTokenSupplier tokens, Duration timeout, Clock clock) {
    this.client = client;
    this.mapper = mapper;
    this.endpoint = endpoint;
    this.tokens = tokens;
    this.timeout = timeout;
    this.clock = clock;
  }

  @Override
  public List<PushGatewayResult> sendBatch(List<PushMessage> messages) {
    var results = new ArrayList<PushGatewayResult>(messages.size());
    for (PushMessage message : messages) {
      results.add(send(message));
    }
    return List.copyOf(results);
  }

  private PushGatewayResult send(PushMessage message) {
    if (message.provider() != PushProvider.FCM) {
      return new PushGatewayResult(Outcome.FAILED, null, "PROVIDER_MISMATCH", Duration.ZERO);
    }
    if (!clock.instant().isBefore(message.expiresAt())) {
      return new PushGatewayResult(Outcome.FAILED, null, "EXPIRED", Duration.ZERO);
    }
    try {
      Map<String, Object> payload = Map.of("message", Map.of(
          "token", message.token(),
          "notification", Map.of("title", message.title(), "body", message.body()),
          "data", Map.of("payloadVersion", "1", "notificationId", message.notificationId(),
              "bindingId", message.bindingId()),
          "apns", Map.of(
              "headers", Map.of(
                  "apns-priority", "10",
                  "apns-expiration", Long.toString(message.expiresAt().getEpochSecond())),
              "payload", Map.of("aps", Map.of("sound", "default")))));
      HttpRequest request = HttpRequest.newBuilder(endpoint)
          .timeout(timeout)
          .header("Authorization", "Bearer " + tokens.accessToken())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        JsonNode body = mapper.readTree(response.body());
        String name = body == null ? "" : body.path("name").asText("");
        return name.isBlank() || name.length() > 255
            ? PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID")
            : PushGatewayResult.confirmed(name);
      }
      return failure(response.statusCode(), response.body());
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return PushGatewayResult.retry("PROVIDER_UNAVAILABLE");
    } catch (Exception exception) {
      return PushGatewayResult.retry("PROVIDER_UNAVAILABLE");
    }
  }

  private PushGatewayResult failure(int status, String rawBody) {
    String providerCode = "";
    try {
      JsonNode error = mapper.readTree(rawBody).path("error");
      providerCode = error.path("status").asText("");
      for (JsonNode detail : error.path("details")) {
        if (!detail.path("errorCode").asText("").isBlank()) {
          providerCode = detail.path("errorCode").asText();
        }
      }
    } catch (Exception ignored) {
      // Status code still determines a stable, non-sensitive result.
    }
    if ("UNREGISTERED".equals(providerCode)) {
      return new PushGatewayResult(Outcome.INVALID_TOKEN, null, "DEVICE_NOT_REGISTERED", Duration.ZERO);
    }
    if (status == 429 || status >= 500
        || "UNAVAILABLE".equals(providerCode) || "INTERNAL".equals(providerCode)
        || "QUOTA_EXCEEDED".equals(providerCode)) {
      return PushGatewayResult.retry("PROVIDER_UNAVAILABLE");
    }
    if (status == 401 || status == 403 || "THIRD_PARTY_AUTH_ERROR".equals(providerCode)) {
      return new PushGatewayResult(
          Outcome.FAILED, null, "PROVIDER_CREDENTIALS_INVALID", Duration.ZERO);
    }
    return new PushGatewayResult(Outcome.FAILED, null, "PROVIDER_REQUEST_REJECTED", Duration.ZERO);
  }

  @Override
  public Map<String, PushGatewayResult> getReceipts(List<String> ticketIds) {
    return Map.of();
  }
}
