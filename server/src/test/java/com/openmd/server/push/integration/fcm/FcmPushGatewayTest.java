package com.openmd.server.push.integration.fcm;

import static org.junit.jupiter.api.Assertions.*;

import com.openmd.server.push.domain.PushProvider;
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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FcmPushGatewayTest {
  private static final Instant NOW = Instant.parse("2026-09-09T00:00:00Z");
  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicReference<String> request = new AtomicReference<>();
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private HttpServer server;
  private int status = 200;
  private String response = "{\"name\":\"projects/nalq/messages/message-1\"}";

  @BeforeEach
  void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  private FcmPushGateway gateway() {
    return new FcmPushGateway(
        HttpClient.newHttpClient(), mapper,
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/messages:send"),
        () -> "oauth-token", Duration.ofSeconds(2), Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private PushMessage message() {
    return new PushMessage(PushProvider.FCM, "fcm-token.test:1234567890", "퀴즈 제목",
        "퀴즈가 완성됐어요.", "notification-1", "binding-1", NOW.plusSeconds(3600));
  }

  @Test
  void sendsIosAlertAndMarksTheSynchronousFcmAcceptanceFinal() throws Exception {
    var result = gateway().sendBatch(List.of(message())).getFirst();

    assertEquals(Outcome.CONFIRMED, result.outcome());
    assertEquals("projects/nalq/messages/message-1", result.ticketId());
    assertEquals("Bearer oauth-token", authorization.get());
    var sent = mapper.readTree(request.get()).path("message");
    assertEquals("퀴즈 제목", sent.path("notification").path("title").asText());
    assertEquals("default", sent.path("apns").path("payload").path("aps").path("sound").asText());
    assertEquals("10", sent.path("apns").path("headers").path("apns-priority").asText());
    assertEquals("1", sent.path("data").path("payloadVersion").asText());
    assertEquals("notification-1", sent.path("data").path("notificationId").asText());
  }

  @Test
  void mapsProviderErrorsWithoutReturningRawMessages() {
    status = 404;
    response = """
        {"error":{"code":404,"message":"private token text","status":"NOT_FOUND",
        "details":[{"@type":"type.googleapis.com/google.firebase.fcm.v1.FcmError",
        "errorCode":"UNREGISTERED"}]}}
        """;
    var result = gateway().sendBatch(List.of(message())).getFirst();
    assertEquals(Outcome.INVALID_TOKEN, result.outcome());
    assertEquals("DEVICE_NOT_REGISTERED", result.errorCode());
    assertFalse(result.toString().contains("private token text"));

    status = 503;
    response = "{\"error\":{\"status\":\"UNAVAILABLE\"}}";
    assertEquals(Outcome.RETRY, gateway().sendBatch(List.of(message())).getFirst().outcome());
  }
}
