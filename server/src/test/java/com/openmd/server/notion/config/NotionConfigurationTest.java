package com.openmd.server.notion.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.notion.integration.HttpNotionOAuthClient;
import com.openmd.server.notion.integration.NotionOAuthException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotionConfigurationTest {

	private HttpServer server;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void appliesReadTimeoutAndSanitizesTheCredentialFromTheFailure() {
		server.createContext("/v1/oauth/token", exchange -> {
			try {
				Thread.sleep(2_000);
				respond(exchange, 200, "{\"access_token\":\"late-response-body\"}");
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			} catch (IOException ignored) {
				// The client is expected to close the timed-out exchange.
			}
		});
		HttpNotionOAuthClient client = client(Duration.ofSeconds(1), Duration.ofMillis(50));

		long startedAt = System.nanoTime();
		NotionOAuthException failure = assertThrows(
			NotionOAuthException.class,
			() -> client.exchangeAuthorizationCode("sensitive-code")
		);
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

		assertTrue(elapsedMillis < 1_000, "read timeout must stop waiting before the fake response completes");
		assertFalse(failure.getMessage().contains("sensitive-code"));
		assertFalse(failure.getMessage().contains("late-response-body"));
		assertNull(failure.getCause());
	}

	@Test
	void sanitizesNotionErrorBodiesAndRejectsNonPositiveTimeouts() {
		server.createContext("/v1/oauth/token", exchange ->
			respond(exchange, 401, "{\"message\":\"sensitive-notion-response\"}"));
		HttpNotionOAuthClient client = client(Duration.ofSeconds(1), Duration.ofSeconds(1));

		NotionOAuthException failure = assertThrows(
			NotionOAuthException.class,
			() -> client.refresh("sensitive-refresh-token")
		);

		assertFalse(failure.getMessage().contains("sensitive-notion-response"));
		assertFalse(failure.getMessage().contains("sensitive-refresh-token"));
		assertNull(failure.getCause());
		assertThrows(IllegalArgumentException.class, () ->
			NotionConfiguration.buildRestClient(baseUrl(), Duration.ZERO, Duration.ofSeconds(1)));
		assertThrows(IllegalArgumentException.class, () ->
			NotionConfiguration.buildRestClient(baseUrl(), Duration.ofSeconds(1), Duration.ofSeconds(-1)));
	}

	private HttpNotionOAuthClient client(Duration connectTimeout, Duration readTimeout) {
		return new HttpNotionOAuthClient(
			NotionConfiguration.buildRestClient(baseUrl(), connectTimeout, readTimeout),
			"client-id", "client-secret", "http://localhost/callback"
		);
	}

	private String baseUrl() {
		return "http://localhost:" + server.getAddress().getPort();
	}

	private void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}
}
