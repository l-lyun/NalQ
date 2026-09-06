package com.openmd.server.push.integration.expo;

import static org.junit.jupiter.api.Assertions.*;

import com.openmd.server.push.dto.model.PushGatewayResult.Outcome;
import com.openmd.server.push.dto.model.PushMessage;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExpoPushGatewayTest {
  private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicReference<String> request = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicInteger calls = new AtomicInteger();
  private HttpServer server;
  private HttpClient client;
  private String response = "{\"data\":[{\"status\":\"ok\",\"id\":\"ticket-1\"}]}";
  private int status = 200;
  private String retryAfter;

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      calls.incrementAndGet();
      request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      if (retryAfter != null) exchange.getResponseHeaders().set("Retry-After", retryAfter);
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
    if (client != null) client.close();
  }

  private ExpoPushGateway gateway() {
    return new ExpoPushGateway(client, mapper,
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
        "test-access-token", Duration.ofSeconds(1), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private PushMessage message() {
    return new PushMessage("ExponentPushToken[test]", "퀴즈 제목", "퀴즈가 완성됐어요.",
        "notification-1", "binding-1", NOW.plusSeconds(3600));
  }

  @Test
  void sendsAbsoluteExpirationAndOnlyMinimalDataWithoutInternalRetries() throws Exception {
    var result = gateway().sendBatch(List.of(message())).getFirst();
    assertEquals(Outcome.ACCEPTED, result.outcome());
    assertEquals("ticket-1", result.ticketId());
    var sent = mapper.readTree(request.get()).get(0);
    assertEquals(NOW.plusSeconds(3600).getEpochSecond(), sent.path("expiration").asLong());
    assertFalse(sent.has("ttl"));
    assertEquals("퀴즈 제목", sent.path("title").asText());
    assertEquals(3, sent.path("data").size());
    assertEquals("notification-1", sent.path("data").path("notificationId").asText());
    assertEquals("Bearer test-access-token", authorization.get());
    assertEquals(1, calls.get());
    assertFalse(message().toString().contains("ExponentPushToken"));
  }

  @Test
  void distinguishesPerDeviceErrorsAndNeverReturnsProviderMessage() {
    response = """
        {"data":[{"status":"error","message":"secret-token-and-title",
          "details":{"error":"DeviceNotRegistered"}},
          {"status":"error","details":{"error":"MessageRateExceeded"}}]}
        """;
    var results = gateway().sendBatch(List.of(message(), message()));
    assertEquals(Outcome.INVALID_TOKEN, results.get(0).outcome());
    assertEquals(Outcome.RETRY, results.get(1).outcome());
    assertFalse(results.toString().contains("secret-token-and-title"));
  }

  @Test
  void treatsHttp429AsRetryAndHonorsRetryAfterWithoutRetryingHere() {
    status = 429;
    retryAfter = "120";
    var result = gateway().sendBatch(List.of(message())).getFirst();
    assertEquals(Outcome.RETRY, result.outcome());
    assertEquals(Duration.ofSeconds(120), result.retryAfter());
    assertEquals(1, calls.get());
  }

  @Test
  void treatsAuthErrorsAsPermanentAndMalformedTicketsAsUncertainRetry() {
    status = 401;
    assertEquals(Outcome.FAILED, gateway().sendBatch(List.of(message())).getFirst().outcome());
    status = 200;
    response = "{\"data\":[]}";
    assertEquals(Outcome.RETRY, gateway().sendBatch(List.of(message())).getFirst().outcome());
    response = "{\"data\":[{\"status\":\"ok\"}]}";
    assertEquals(Outcome.RETRY, gateway().sendBatch(List.of(message())).getFirst().outcome());
  }

  @Test
  void rejectedReceiptLookupDoesNotAssertThatTheMessageFailedDelivery() {
    status = 401;
    response = "credential rejected with private provider detail";
    var result = gateway().getReceipts(List.of("accepted-ticket")).get("accepted-ticket");
    assertEquals(Outcome.RETRY, result.outcome());
    assertEquals("RECEIPT_LOOKUP_REJECTED", result.errorCode());
    assertEquals(1, calls.get());
    assertFalse(result.toString().contains("private provider detail"));
  }

  @Test
  void returnsPendingForMissingReceiptsAndNeverResendsTheirPayloads() throws Exception {
    response = "{\"data\":{\"one\":{\"status\":\"ok\"}}}";
    var results = gateway().getReceipts(List.of("one", "two"));
    assertEquals(Outcome.ACCEPTED, results.get("one").outcome());
    assertEquals(Outcome.PENDING, results.get("two").outcome());
    assertEquals(2, mapper.readTree(request.get()).path("ids").size());
    assertFalse(request.get().contains("title"));
  }

  @Test
  void networkFailureIsRetryableWithoutLeakingRequestData() {
    var gateway = gateway();
    server.stop(0);
    var result = gateway.sendBatch(List.of(message())).getFirst();
    assertEquals(Outcome.RETRY, result.outcome());
    assertEquals("PROVIDER_UNAVAILABLE", result.errorCode());
  }

  @Test
  void rejectsExpiredMessagesWithoutContactingProvider() {
    var expired = new PushMessage("ExponentPushToken[test]", "제목", "본문", "n", "b", NOW);
    assertEquals(Outcome.FAILED, gateway().sendBatch(List.of(expired)).getFirst().outcome());
    assertEquals(0, calls.get());
  }

  @Test
  void malformedJsonAndNullBodiesRemainUncertainRatherThanSuccessful() {
    response = "not-json";
    assertEquals("PROVIDER_RESPONSE_INVALID",
        gateway().sendBatch(List.of(message())).getFirst().errorCode());
    response = "null";
    assertEquals(Outcome.RETRY, gateway().sendBatch(List.of(message())).getFirst().outcome());
  }

  @Test
  void expiredEntryDoesNotShiftTheRemainingTicketToTheWrongDevice() throws Exception {
    var expired = new PushMessage("ExponentPushToken[expired]", "제목", "본문", "n", "b", NOW);
    var results = gateway().sendBatch(List.of(expired, message()));
    assertEquals(Outcome.FAILED, results.getFirst().outcome());
    assertEquals("ticket-1", results.get(1).ticketId());
    assertEquals(1, mapper.readTree(request.get()).size());
  }

  @Test
  void receiptTokenInvalidationIsReturnedOnlyForTheMatchingTicket() {
    response = """
        {"data":{"one":{"status":"error","details":{"error":"DeviceNotRegistered"}},
          "two":{"status":"ok"}}}
        """;
    var results = gateway().getReceipts(List.of("one", "two"));
    assertEquals(Outcome.INVALID_TOKEN, results.get("one").outcome());
    assertEquals(Outcome.ACCEPTED, results.get("two").outcome());
  }

  @Test
  void deadlineIncludesSlowResponseBody() {
    server.removeContext("/");
    server.createContext("/", exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.sendResponseHeaders(200, 1024);
      exchange.getResponseBody().write('{');
      exchange.getResponseBody().flush();
      try {
        Thread.sleep(800);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
      } finally {
        exchange.close();
      }
    });
    var gateway = new ExpoPushGateway(client, mapper,
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
        "", Duration.ofMillis(150), Clock.fixed(NOW, ZoneOffset.UTC));
    assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
      var result = gateway.sendBatch(List.of(message())).getFirst();
      assertEquals(Outcome.RETRY, result.outcome());
      assertEquals("PROVIDER_UNAVAILABLE", result.errorCode());
    });
  }
}
