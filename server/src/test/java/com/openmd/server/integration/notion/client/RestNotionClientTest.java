package com.openmd.server.integration.notion.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RestNotionClientTest {
	private RecordingTransport transport;
	private MutableClock clock;
	private RestNotionClient client;

	@BeforeEach
	void setUp() {
		transport = new RecordingTransport();
		clock = new MutableClock(Instant.parse("2026-09-01T00:00:00Z"));
		client = new RestNotionClient(
			transport, new ObjectMapper(), "client-id", "client-secret", "https://openmd.test/callback",
			"https://api.notion.test/v1/oauth/authorize", clock
		);
	}

	@Test
	void mapsOfficialNullableTokenFieldsWithoutPersistingProviderOwnerPayload() {
		transport.respond(200, """
			{"access_token":"access","token_type":"bearer","refresh_token":null,
			 "workspace_id":"workspace-a","workspace_name":null,"owner":{"type":"workspace"}}
			""");

		NotionTokenGrant grant = client.exchangeAuthorizationCode("code");

		assertEquals("access", grant.accessToken());
		assertEquals("workspace-a", grant.workspaceId());
		assertNull(grant.refreshToken());
		assertNull(grant.workspaceName());
		assertEquals("POST", transport.requests.getFirst().method());
		assertEquals("/v1/oauth/token", transport.requests.getFirst().path());
		assertEquals("2026-03-11", transport.requests.getFirst().headers().get("Notion-Version"));
		assertEquals("authorization_code", transport.requests.getFirst().body().get("grant_type"));
	}

	@Test
	void readsOfficialMarkdownFieldWithTranscriptExplicitlyDisabled() {
		transport.respond(200, """
			{"object":"page_markdown","id":"page-1","markdown":"# 본문","truncated":false,
			 "unknown_block_ids":[]}
			""");

		NotionMarkdown markdown = client.retrieveMarkdown("access", "page-1");

		assertEquals("# 본문", markdown.markdown());
		assertEquals(false, markdown.truncated());
		assertEquals("/v1/pages/page-1/markdown?include_transcript=false", transport.requests.getFirst().path());
	}

	@Test
	void retriesOneIdempotentGetAndLimitsTheSecondAttemptToTheRemainingTwentySecondBudget() {
		transport.respondThenAdvance(500, "{}", Duration.ofSeconds(12));
		transport.respond(200, """
			{"object":"page","id":"page-1","last_edited_time":"2026-09-01T05:30:00Z",
			 "properties":{"Name":{"type":"title","title":[{"plain_text":"재시도 성공"}]}}}
			""");

		assertEquals("재시도 성공", client.retrievePage("access", "page-1").title());
		assertEquals(Duration.ofSeconds(15), transport.requests.get(0).timeout());
		assertEquals(Duration.ofSeconds(8), transport.requests.get(1).timeout());
	}

	@Test
	void retriesRateLimitOnceButDoesNotRetryOrdinaryClientErrors() {
		transport.respond(529, Map.of("Retry-After", List.of("0")), "{}");
		transport.respond(200, "{\"results\":[],\"next_cursor\":null}");

		assertEquals(List.of(), client.searchPages("access", null, null).items());
		assertEquals(2, transport.requests.size());

		transport.requests.clear();
		transport.respond(400, "{}");
		assertThrows(NotionClientException.class, () -> client.retrievePage("access", "page"));
		assertEquals(1, transport.requests.size());
	}

	@Test
	void waitsForRetryAfterPlusDeterministicJitterBeforeOneRateLimitRetry() {
		List<Duration> sleeps = new ArrayList<>();
		client = new RestNotionClient(
			transport, new ObjectMapper(), "client-id", "client-secret", "https://openmd.test/callback",
			"https://api.notion.test/v1/oauth/authorize", clock,
			() -> 37L, sleeps::add
		);
		transport.respond(429, Map.of("Retry-After", List.of("2")), "{}");
		transport.respond(200, "{\"results\":[],\"next_cursor\":null}");

		assertEquals(List.of(), client.searchPages("access", null, null).items());
		assertEquals(List.of(Duration.ofMillis(2_037)), sleeps);
		assertEquals(2, transport.requests.size());
	}

	@Test
	void acceptsOnlyAnExplicitBooleanActiveValueFromIntrospection() {
		transport.respond(200, "{}");
		assertThrows(NotionClientException.class, () -> client.introspect("access"));

		transport.respond(200, "{\"active\":\"false\"}");
		assertThrows(NotionClientException.class, () -> client.introspect("access"));

		transport.respond(200, "{\"active\":false}");
		assertEquals(false, client.introspect("access"));
	}

	private record Request(String method, String path, Map<String, String> headers,
		Map<String, Object> body, Duration timeout) {}
	private record Scripted(NotionHttpResponse response, Duration advance) {}

	private final class RecordingTransport implements NotionHttpTransport {
		private final Deque<Scripted> responses = new ArrayDeque<>();
		private final List<Request> requests = new ArrayList<>();

		void respond(int status, String body) { respondThenAdvance(status, body, Duration.ZERO); }
		void respond(int status, Map<String, List<String>> headers, String body) {
			responses.add(new Scripted(new NotionHttpResponse(status, headers, body), Duration.ZERO));
		}
		void respondThenAdvance(int status, String body, Duration advance) {
			responses.add(new Scripted(new NotionHttpResponse(status, Map.of(), body), advance));
		}

		@Override
		public NotionHttpResponse exchange(String method, String path, Map<String, String> headers,
			Map<String, Object> body, Duration timeout) {
			requests.add(new Request(method, path, headers, body, timeout));
			Scripted scripted = responses.removeFirst();
			clock.advance(scripted.advance());
			return scripted.response();
		}
	}

	private static final class MutableClock extends Clock {
		private Instant instant;
		private MutableClock(Instant instant) { this.instant = instant; }
		void advance(Duration duration) { instant = instant.plus(duration); }
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return instant; }
	}
}
