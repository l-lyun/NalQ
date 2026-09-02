package com.openmd.server.integration.notion.client;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class RestNotionClient implements NotionClient {
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
	private final NotionHttpTransport transport;
	private final ObjectMapper mapper;
	private final String clientId;
	private final String clientSecret;
	private final String callbackUri;
	private final String authorizeUrl;
	private final Clock clock;
	private final LongSupplier retryJitterMillis;
	private final RetrySleeper retrySleeper;

	public RestNotionClient(NotionHttpTransport transport, ObjectMapper mapper, String clientId,
		String clientSecret, String callbackUri, String authorizeUrl, Clock clock) {
		this(transport, mapper, clientId, clientSecret, callbackUri, authorizeUrl, clock,
			() -> ThreadLocalRandom.current().nextLong(101), delay -> Thread.sleep(delay.toMillis()));
	}

	RestNotionClient(NotionHttpTransport transport, ObjectMapper mapper, String clientId,
		String clientSecret, String callbackUri, String authorizeUrl, Clock clock,
		LongSupplier retryJitterMillis, RetrySleeper retrySleeper) {
		this.transport = transport;
		this.mapper = mapper;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.callbackUri = callbackUri;
		this.authorizeUrl = authorizeUrl;
		this.clock = clock;
		this.retryJitterMillis = retryJitterMillis;
		this.retrySleeper = retrySleeper;
	}

	@Override
	public String authorizationUrl(String state) {
		return UriComponentsBuilder.fromUriString(authorizeUrl)
			.queryParam("client_id", clientId).queryParam("response_type", "code")
			.queryParam("owner", "user").queryParam("redirect_uri", callbackUri)
			.queryParam("state", state).build().encode().toUriString();
	}

	@Override public NotionTokenGrant exchangeAuthorizationCode(String code) {
		return within(() -> token(Map.of(
			"grant_type", "authorization_code", "code", code, "redirect_uri", callbackUri
		)));
	}

	@Override public NotionTokenGrant refresh(String refreshToken) {
		return within(() -> token(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken)));
	}

	@Override public boolean revoke(String accessToken) {
		return within(() -> {
			NotionHttpResponse response = oauthPost("/v1/oauth/revoke", Map.of("token", accessToken));
			ensureOAuthSuccess(response);
			return true;
		});
	}

	@Override public boolean introspect(String accessToken) {
		return within(() -> {
			NotionHttpResponse response = oauthPost("/v1/oauth/introspect", Map.of("token", accessToken));
			ensureOAuthSuccess(response);
			JsonNode active = json(response).get("active");
			if (active == null || !active.isBoolean()) {
				throw new NotionClientException(NotionClientFailure.TEMPORARY);
			}
			return active.asBoolean();
		});
	}

	@Override
	public NotionPageSearch searchPages(String accessToken, String cursor, String query) {
		return within(() -> {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("filter", Map.of("property", "object", "value", "page"));
			body.put("sort", Map.of("direction", "descending", "timestamp", "last_edited_time"));
			body.put("page_size", 20);
			if (cursor != null) body.put("start_cursor", cursor);
			if (query != null) body.put("query", query);
			JsonNode root = contentJson(() -> request("POST", "/v1/search", bearer(accessToken), body), false);
			List<NotionPageItem> items = new ArrayList<>();
			for (JsonNode result : root.path("results")) {
				if (!"page".equals(result.path("object").asText())) continue;
				items.add(new NotionPageItem(result.path("id").asText(), title(result),
					Instant.parse(result.path("last_edited_time").asText())));
			}
			return new NotionPageSearch(items, nullableText(root.get("next_cursor")));
		});
	}

	@Override public NotionPage retrievePage(String accessToken, String pageId) {
		return within(() -> new NotionPage(title(contentJson(
			() -> request("GET", "/v1/pages/" + path(pageId), bearer(accessToken), null), true
		))));
	}

	@Override public NotionMarkdown retrieveMarkdown(String accessToken, String pageId) {
		return within(() -> {
			JsonNode root = contentJson(() -> request("GET", "/v1/pages/" + path(pageId)
				+ "/markdown?include_transcript=false", bearer(accessToken), null), true);
			List<String> unknown = new ArrayList<>();
			for (JsonNode id : root.path("unknown_block_ids")) unknown.add(id.asText());
			return new NotionMarkdown(root.path("markdown").asText(""),
				root.path("truncated").asBoolean(false), unknown);
		});
	}

	private NotionTokenGrant token(Map<String, Object> body) {
		NotionHttpResponse response = oauthPost("/v1/oauth/token", body);
		if (response.status() == 400) throw new NotionClientException(NotionClientFailure.INVALID_GRANT);
		ensureOAuthSuccess(response);
		JsonNode root = json(response);
		return new NotionTokenGrant(root.path("access_token").asText(), nullableText(root.get("refresh_token")),
			root.path("workspace_id").asText(), nullableText(root.get("workspace_name")));
	}

	private NotionHttpResponse oauthPost(String path, Map<String, Object> body) {
		String basic = Base64.getEncoder().encodeToString(
			(clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)
		);
		return request("POST", path, Map.of("Authorization", "Basic " + basic), body);
	}

	private JsonNode contentJson(Supplier<NotionHttpResponse> request, boolean retryServerErrors) {
		NotionHttpResponse response = retryOnce(request, retryServerErrors);
		if (response.status() == 401) throw new NotionClientException(NotionClientFailure.UNAUTHORIZED);
		if (response.status() == 400 || response.status() == 403 || response.status() == 404) {
			throw new NotionClientException(NotionClientFailure.NOT_ACCESSIBLE);
		}
		if (response.status() < 200 || response.status() >= 300) {
			throw new NotionClientException(NotionClientFailure.TEMPORARY);
		}
		return json(response);
	}

	private NotionHttpResponse retryOnce(Supplier<NotionHttpResponse> request, boolean retryServerErrors) {
		NotionHttpResponse first = request.get();
		int status = first.status();
		boolean retryable = status == 429 || status == 529
			|| (retryServerErrors && (status == 500 || status == 502 || status == 503 || status == 504));
		if (!retryable) return first;
		Duration delay = retryAfter(first);
		if (status == 429 || status == 529) {
			long jitterMillis = Math.max(0L, Math.min(100L, retryJitterMillis.getAsLong()));
			delay = delay.plusMillis(jitterMillis);
		}
		Duration remaining = NotionRequestBudget.remaining(clock, READ_TIMEOUT);
		if (delay.compareTo(remaining) >= 0) return first;
		if (!delay.isZero()) {
			try {
				retrySleeper.sleep(delay);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				return first;
			}
		}
		return request.get();
	}

	private NotionHttpResponse request(String method, String path, Map<String, String> headers,
		Map<String, Object> body) {
		Map<String, String> requestHeaders = new LinkedHashMap<>(headers);
		requestHeaders.put("Notion-Version", "2026-03-11");
		return transport.exchange(method, path, requestHeaders, body,
			NotionRequestBudget.remaining(clock, READ_TIMEOUT));
	}

	private void ensureOAuthSuccess(NotionHttpResponse response) {
		if (response.status() == 401 || response.status() == 403) {
			throw new NotionClientException(NotionClientFailure.UNAUTHORIZED);
		}
		if (response.status() < 200 || response.status() >= 300) {
			throw new NotionClientException(NotionClientFailure.TEMPORARY);
		}
	}

	private JsonNode json(NotionHttpResponse response) {
		try {
			return mapper.readTree(response.body());
		} catch (JacksonException exception) {
			throw new NotionClientException(NotionClientFailure.TEMPORARY, exception);
		}
	}

	private <T> T within(Supplier<T> operation) {
		return NotionRequestBudget.within(clock, operation);
	}

	private static Map<String, String> bearer(String token) {
		return Map.of("Authorization", "Bearer " + token);
	}

	private static Duration retryAfter(NotionHttpResponse response) {
		String header = response.firstHeader("Retry-After");
		if (header == null) return Duration.ZERO;
		try {
			long seconds = Long.parseLong(header);
			return seconds < 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
		} catch (NumberFormatException exception) {
			return Duration.ZERO;
		}
	}

	private static String title(JsonNode page) {
		for (var field : page.path("properties").properties()) {
			JsonNode property = field.getValue();
			if (!"title".equals(property.path("type").asText())) continue;
			StringBuilder title = new StringBuilder();
			for (JsonNode text : property.path("title")) title.append(text.path("plain_text").asText(""));
			return title.toString();
		}
		return "";
	}

	private static String nullableText(JsonNode node) {
		return node == null || node.isNull() ? null : node.asText();
	}

	private static String path(String value) {
		return UriComponentsBuilder.newInstance().pathSegment(value).build().encode().getPath().substring(1);
	}

	@FunctionalInterface
	interface RetrySleeper {
		void sleep(Duration delay) throws InterruptedException;
	}
}
