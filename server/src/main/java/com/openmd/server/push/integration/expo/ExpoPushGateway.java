package com.openmd.server.push.integration.expo;

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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Expo wire details remain inside this adapter. Provider messages are never logged or returned. */
public final class ExpoPushGateway implements PushGateway {
  private final HttpClient client;
  private final ObjectMapper mapper;
  private final URI base;
  private final String accessToken;
  private final Duration timeout;
  private final Clock clock;

  public ExpoPushGateway(HttpClient client, ObjectMapper mapper, URI base, String accessToken,
      Duration timeout, Clock clock) {
    if (!base.toString().endsWith("/") || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Invalid push gateway configuration");
    }
    this.client = client;
    this.mapper = mapper;
    this.base = base;
    this.accessToken = accessToken;
    this.timeout = timeout;
    this.clock = clock;
  }

  @Override
  public List<PushGatewayResult> sendBatch(List<PushMessage> messages) {
    if (messages.isEmpty()) return List.of();
    if (messages.size() > 100) throw new IllegalArgumentException("Push batch exceeds 100");
    var results = new ArrayList<PushGatewayResult>();
    var positions = new ArrayList<Integer>();
    var payload = new ArrayList<Map<String, Object>>();
    for (PushMessage message : messages) {
      int position = results.size();
      if (!clock.instant().isBefore(message.expiresAt())) {
        results.add(new PushGatewayResult(Outcome.FAILED, null, "EXPIRED", Duration.ZERO));
        continue;
      }
      results.add(null);
      positions.add(position);
      payload.add(Map.of(
          "to", message.token(), "title", message.title(), "body", message.body(),
          "sound", "default", "expiration", message.expiresAt().getEpochSecond(),
          "data", Map.of("payloadVersion", 1, "notificationId", message.notificationId(),
              "bindingId", message.bindingId())));
    }
    if (payload.isEmpty()) return List.copyOf(results);
    WireResponse response = post("send", payload);
    if (response.failure() != null) {
      positions.forEach(i -> results.set(i, response.failure()));
      return List.copyOf(results);
    }
    JsonNode data = response.body().path("data");
    if (!data.isArray() || data.size() != positions.size()) {
      positions.forEach(i -> results.set(i, PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID")));
      return List.copyOf(results);
    }
    for (int i = 0; i < positions.size(); i++) {
      results.set(positions.get(i), parseResult(data.get(i), true));
    }
    return List.copyOf(results);
  }

  @Override
  public Map<String, PushGatewayResult> getReceipts(List<String> ticketIds) {
    if (ticketIds.isEmpty()) return Map.of();
    if (ticketIds.size() > 1000) throw new IllegalArgumentException("Receipt batch exceeds 1000");
    WireResponse response = post("getReceipts", Map.of("ids", ticketIds));
    var results = new LinkedHashMap<String, PushGatewayResult>();
    if (response.failure() != null) {
      // A failed lookup cannot establish failure of an already accepted message.
      // The receipt worker bounds these lookups to 24h; it never resends the message.
      PushGatewayResult failure = response.failure().outcome() == Outcome.FAILED
          ? PushGatewayResult.retry("RECEIPT_LOOKUP_REJECTED") : response.failure();
      ticketIds.forEach(id -> results.put(id, failure));
      return Map.copyOf(results);
    }
    JsonNode data = response.body().path("data");
    if (!data.isObject()) {
      ticketIds.forEach(id -> results.put(id, PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID")));
      return Map.copyOf(results);
    }
    for (String id : ticketIds) {
      results.put(id, data.has(id) ? parseResult(data.get(id), false)
          : new PushGatewayResult(Outcome.PENDING, null, null, Duration.ZERO));
    }
    return Map.copyOf(results);
  }

  private PushGatewayResult parseResult(JsonNode node, boolean ticket) {
    if ("ok".equals(node.path("status").asText())) {
      String id = node.path("id").asText("");
      if (ticket && (id.isBlank() || id.length() > 255)) {
        return PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID");
      }
      return PushGatewayResult.accepted(ticket ? id : null);
    }
    if (!"error".equals(node.path("status").asText())) {
      return PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID");
    }
    return switch (node.path("details").path("error").asText("")) {
      case "DeviceNotRegistered" ->
          new PushGatewayResult(Outcome.INVALID_TOKEN, null, "DEVICE_NOT_REGISTERED", Duration.ZERO);
      case "MessageRateExceeded" -> PushGatewayResult.retry("MESSAGE_RATE_EXCEEDED");
      case "MessageTooBig" ->
          new PushGatewayResult(Outcome.FAILED, null, "MESSAGE_TOO_BIG", Duration.ZERO);
      case "MismatchSenderId", "InvalidCredentials" ->
          new PushGatewayResult(Outcome.FAILED, null, "PROVIDER_CREDENTIALS_INVALID", Duration.ZERO);
      default -> new PushGatewayResult(Outcome.FAILED, null, "PROVIDER_ERROR", Duration.ZERO);
    };
  }

  private WireResponse post(String path, Object payload) {
    CompletableFuture<HttpResponse<String>> future = null;
    try {
      HttpRequest.Builder builder = HttpRequest.newBuilder(base.resolve(path))
          .timeout(timeout).header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
      if (accessToken != null && !accessToken.isBlank()) {
        builder.header("Authorization", "Bearer " + accessToken);
      }
      future = client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
      // Includes body consumption: an idle/slow body must not outlive the worker lease.
      HttpResponse<String> response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      int status = response.statusCode();
      if (status < 200 || status >= 300) {
        boolean retry = status == 429 || status >= 500;
        return new WireResponse(null, new PushGatewayResult(
            retry ? Outcome.RETRY : Outcome.FAILED, null,
            retry ? "PROVIDER_UNAVAILABLE" : "PROVIDER_REQUEST_REJECTED", retryDelay(response)));
      }
      JsonNode root = mapper.readTree(response.body());
      if (root == null || !root.isObject()) {
        return new WireResponse(null, PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID"));
      }
      return new WireResponse(root, null);
    } catch (InterruptedException exception) {
      if (future != null) future.cancel(true);
      Thread.currentThread().interrupt();
      return new WireResponse(null, PushGatewayResult.retry("PROVIDER_UNAVAILABLE"));
    } catch (ExecutionException | TimeoutException exception) {
      if (future != null) future.cancel(true);
      return new WireResponse(null, PushGatewayResult.retry("PROVIDER_UNAVAILABLE"));
    } catch (JacksonException exception) {
      return new WireResponse(null, PushGatewayResult.retry("PROVIDER_RESPONSE_INVALID"));
    }
  }

  private Duration retryDelay(HttpResponse<?> response) {
    String value = response.headers().firstValue("Retry-After").orElse("");
    try {
      long seconds = Long.parseLong(value);
      return Duration.ofSeconds(Math.max(0, Math.min(86400, seconds)));
    } catch (NumberFormatException ignored) {
      try {
        Duration delay = Duration.between(clock.instant(),
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
        return delay.isNegative() ? Duration.ZERO : delay;
      } catch (java.time.DateTimeException invalid) {
        return Duration.ZERO;
      }
    }
  }

  private record WireResponse(JsonNode body, PushGatewayResult failure) {}
}
