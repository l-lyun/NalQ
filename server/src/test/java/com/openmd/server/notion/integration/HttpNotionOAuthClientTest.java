package com.openmd.server.notion.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.notion.dto.model.NotionOAuthGrant;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class HttpNotionOAuthClientTest {

	private HttpServer server;
	private final List<String> bodies = new ArrayList<>();
	private final List<String> authorizationHeaders = new ArrayList<>();

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/v1/oauth/token", exchange -> respond(exchange, 200, """
			{"access_token":"access-1","refresh_token":"refresh-1","bot_id":"bot-1",\
			"workspace_id":"workspace-1","workspace_name":"팀 공간","workspace_icon":"https://example/icon.png"}
			"""));
		server.createContext("/v1/oauth/revoke", exchange -> respond(exchange, 200, "{}"));
		server.start();
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void exchangesRefreshesAndRevokesUsingBasicClientAuthentication() {
		HttpNotionOAuthClient client = new HttpNotionOAuthClient(
			RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort()).build(),
			"client-id", "client-secret", "http://localhost/callback"
		);

		NotionOAuthGrant exchanged = client.exchangeAuthorizationCode("code-1");
		NotionOAuthGrant refreshed = client.refresh("refresh-before");
		client.revoke("access-before");

		assertEquals("access-1", exchanged.accessToken());
		assertEquals("refresh-1", refreshed.refreshToken());
		assertEquals("workspace-1", exchanged.workspaceId());
		assertEquals("팀 공간", exchanged.workspaceName());
		assertTrue(bodies.get(0).contains("authorization_code"));
		assertTrue(bodies.get(0).contains("code-1"));
		assertTrue(bodies.get(1).contains("refresh_token"));
		assertTrue(bodies.get(2).contains("access-before"));
		assertTrue(authorizationHeaders.stream().allMatch(value -> value.startsWith("Basic ")));
	}

	private void respond(HttpExchange exchange, int status, String body) throws IOException {
		bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
		authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		exchange.getResponseBody().write(bytes);
		exchange.close();
	}
}
