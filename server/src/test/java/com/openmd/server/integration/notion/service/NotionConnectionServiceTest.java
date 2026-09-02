package com.openmd.server.integration.notion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.integration.notion.client.NotionClient;
import com.openmd.server.integration.notion.client.NotionClientException;
import com.openmd.server.integration.notion.client.NotionClientFailure;
import com.openmd.server.integration.notion.client.NotionMarkdown;
import com.openmd.server.integration.notion.client.NotionPage;
import com.openmd.server.integration.notion.client.NotionTokenGrant;
import com.openmd.server.integration.notion.crypto.EncryptedToken;
import com.openmd.server.integration.notion.crypto.TokenCipher;
import com.openmd.server.integration.notion.crypto.TokenType;
import com.openmd.server.integration.notion.domain.NotionConnection;
import com.openmd.server.integration.notion.dto.model.NotionOAuthState;
import com.openmd.server.integration.notion.error.NotionErrorCode;
import com.openmd.server.integration.notion.repository.NotionConnectionRepository;
import com.openmd.server.integration.notion.repository.NotionOAuthStateStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotionConnectionServiceTest {

	private final NotionConnectionRepository connections = mock(NotionConnectionRepository.class);
	private final NotionOAuthStateStore states = mock(NotionOAuthStateStore.class);
	private final NotionClient client = mock(NotionClient.class);
	private final TokenCipher cipher = mock(TokenCipher.class);
	private final UserRepository users = mock(UserRepository.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-09-01T06:00:00Z"), ZoneOffset.UTC);
	private final NotionConnectionService service = new NotionConnectionService(
		connections, states, client, cipher, users, new NotionMarkdownProcessor(), clock,
		List.of("https://app.openmd.example/learning/import/notion"),
		"https://app.openmd.example/learning/import/notion"
	);

	@BeforeEach
	void tokenCipherFixture() {
		when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(
			com.openmd.server.auth.domain.User.pending("user@example.com", "user@example.com", "hash")
		));
		when(cipher.encrypt(any(Long.class), any(String.class), any(TokenType.class), any(String.class)))
			.thenReturn(new EncryptedToken(new byte[]{1}, new byte[12], "v1"));
		when(cipher.decrypt(any(Long.class), any(String.class), any(TokenType.class), any(EncryptedToken.class)))
			.thenReturn("access-token");
	}

	@Test
	void importsLatestTitleThenCompleteMarkdownWithoutCreatingServerDraft() {
		NotionConnection connection = NotionConnection.connected(
			7L, "workspace-a", null,
			new EncryptedToken(new byte[]{1}, new byte[12], "v1"), null, clock.instant()
		);
		when(connections.findByUserIdForUpdate(7L)).thenReturn(Optional.of(connection));
		when(client.retrievePage("access-token", "page-1")).thenReturn(new NotionPage("최신 제목"));
		when(client.retrieveMarkdown("access-token", "page-1"))
			.thenReturn(new NotionMarkdown("# 첫 줄<br>둘째", false, List.of()));

		var imported = service.importPage(7L, "page-1");

		assertEquals("NOTION", imported.sourceType());
		assertEquals("최신 제목", imported.title());
		assertEquals("# 첫 줄  \n둘째", imported.content());
		var order = org.mockito.Mockito.inOrder(client);
		order.verify(client).retrievePage("access-token", "page-1");
		order.verify(client).retrieveMarkdown("access-token", "page-1");
	}

	@Test
	void differentWorkspaceCallbackRevokesNewCredentialAndPreservesExistingConnection() {
		NotionConnection existing = NotionConnection.connected(
			7L, "workspace-a", "기존", new EncryptedToken(new byte[]{1}, new byte[12], "v1"), null,
			clock.instant()
		);
		NotionOAuthState state = new NotionOAuthState(
			7L, "https://app.openmd.example/learning/import/notion", "REAUTHORIZE", clock.instant(),
			"workspace-a", 0L
		);
		when(states.find("state")).thenReturn(Optional.of(state));
		when(states.consume("state")).thenReturn(Optional.of(state));
		when(connections.findByUserIdForUpdate(7L)).thenReturn(Optional.of(existing));
		when(client.exchangeAuthorizationCode("code"))
			.thenReturn(new NotionTokenGrant("new-access", "new-refresh", "workspace-b", "새 공간"));

		String redirect = service.completeAuthorization("state", "code", null);

		assertEquals(
			"https://app.openmd.example/learning/import/notion?outcome=failed&error=NOTION_WORKSPACE_MISMATCH",
			redirect
		);
		verify(client).revoke("new-access");
		verify(connections, never()).save(any());
		assertEquals("workspace-a", existing.getWorkspaceId());
	}

	@Test
	void preservesAnUnconfirmedMismatchedCredentialForLaterRevocation() {
		NotionConnection existing = NotionConnection.connected(
			7L, "workspace-a", "기존", new EncryptedToken(new byte[]{1}, new byte[12], "v1"), null,
			clock.instant()
		);
		NotionOAuthState state = new NotionOAuthState(
			7L, "https://app.openmd.example/learning/import/notion", "REAUTHORIZE", clock.instant(),
			"workspace-a", 0L
		);
		when(states.find("state")).thenReturn(Optional.of(state));
		when(states.consume("state")).thenReturn(Optional.of(state));
		when(connections.findByUserIdForUpdate(7L)).thenReturn(Optional.of(existing));
		when(client.exchangeAuthorizationCode("code"))
			.thenReturn(new NotionTokenGrant("new-access", "new-refresh", "workspace-b", "새 공간"));
		when(client.revoke("new-access")).thenThrow(new NotionClientException(NotionClientFailure.TEMPORARY));
		when(client.introspect("new-access")).thenThrow(new NotionClientException(NotionClientFailure.TEMPORARY));

		BusinessException exception = assertThrows(
			BusinessException.class,
			() -> service.completeAuthorization("state", "code", null)
		);

		assertEquals(NotionErrorCode.TEMPORARILY_UNAVAILABLE, exception.getErrorCode());
		assertTrue(existing.hasPendingRevocation());
		verify(connections, never()).save(any());
	}

	@Test
	void retriesAPreservedPendingRevocationBeforeStartingAnotherAuthorization() {
		NotionConnection existing = NotionConnection.connected(
			7L, "workspace-a", "기존", new EncryptedToken(new byte[]{1}, new byte[12], "v1"), null,
			clock.instant()
		);
		existing.rememberPendingRevocation(
			"workspace-b", new EncryptedToken(new byte[]{2}, new byte[12], "v1"), clock.instant()
		);
		when(connections.findByUserId(7L)).thenReturn(Optional.of(existing));
		when(client.revoke("access-token")).thenReturn(true);
		when(client.authorizationUrl(any())).thenReturn("https://notion.test/authorize");

		service.startAuthorization(7L, "https://app.openmd.example/learning/import/notion");

		assertFalse(existing.hasPendingRevocation());
		verify(client).revoke("access-token");
		verify(states).save(any(), any(), any());
	}

	@Test
	void authorizationStartLocksTheUserBeforeSavingState() {
		when(connections.findByUserId(7L)).thenReturn(Optional.empty());
		when(client.authorizationUrl(any())).thenReturn("https://notion.test/authorize");

		service.startAuthorization(7L, "https://app.openmd.example/learning/import/notion");

		var order = inOrder(users, states);
		order.verify(users).findByIdForUpdate(7L);
		order.verify(states).save(any(), any(), any());
	}

	@Test
	void appendsCallbackOutcomeBeforeAnExistingFragment() {
		NotionConnectionService fragmentService = new NotionConnectionService(
			connections, states, client, cipher, users, new NotionMarkdownProcessor(), clock,
			List.of("https://app.openmd.example/import#section"),
			"https://app.openmd.example/import#section"
		);
		NotionOAuthState state = new NotionOAuthState(
			7L, "https://app.openmd.example/import#section", "CONNECT", clock.instant(), null, null
		);
		when(states.find("state")).thenReturn(Optional.of(state));
		when(states.consume("state")).thenReturn(Optional.of(state));
		when(connections.findByUserIdForUpdate(7L)).thenReturn(Optional.empty());
		when(client.exchangeAuthorizationCode("code"))
			.thenReturn(new NotionTokenGrant("access", null, "workspace-a", null));

		assertEquals(
			"https://app.openmd.example/import?outcome=connected#section",
			fragmentService.completeAuthorization("state", "code", null)
		);
	}

	@Test
	void missingOrReusedStateNeverExchangesCodeAndUsesOnlyFixedFailureUri() {
		when(states.find("missing")).thenReturn(Optional.empty());

		String redirect = service.completeAuthorization("missing", "secret-code", null);

		assertEquals(
			"https://app.openmd.example/learning/import/notion?outcome=failed&error=NOTION_CONNECTION_REQUIRED",
			redirect
		);
		verify(client, never()).exchangeAuthorizationCode(any());
	}

	@Test
	void failedRevocationPreservesLocalConnection() {
		NotionConnection existing = NotionConnection.connected(
			7L, "workspace-a", "기존", new EncryptedToken(new byte[]{1}, new byte[12], "v1"), null,
			clock.instant()
		);
		when(connections.findByUserIdForUpdate(7L)).thenReturn(Optional.of(existing));
		when(client.revoke("access-token")).thenReturn(false);
		when(client.introspect("access-token")).thenReturn(true);

		BusinessException exception = assertThrows(BusinessException.class, () -> service.disconnect(7L));

		assertEquals(NotionErrorCode.TEMPORARILY_UNAVAILABLE, exception.getErrorCode());
		verify(connections, never()).delete(any());
		assertFalse(existing.isReauthRequired());
	}

	@Test
	void refreshesAtMostOnceThenRetriesTheOriginalImportWithTheNewCredential() {
		NotionConnection connection = NotionConnection.connected(
			7L, "workspace-a", null,
			new EncryptedToken(new byte[]{1}, new byte[12], "v1"),
			new EncryptedToken(new byte[]{2}, new byte[12], "v1"), clock.instant()
		);
		when(connections.findByUserIdForUpdate(7L)).thenReturn(Optional.of(connection));
		when(client.retrievePage("access-token", "page-1"))
			.thenThrow(new com.openmd.server.integration.notion.client.NotionClientException(
				com.openmd.server.integration.notion.client.NotionClientFailure.UNAUTHORIZED
			));
		when(client.refresh("access-token"))
			.thenReturn(new NotionTokenGrant("new-access", "new-refresh", "workspace-a", null));
		when(client.retrievePage("new-access", "page-1")).thenReturn(new NotionPage("제목"));
		when(client.retrieveMarkdown("new-access", "page-1"))
			.thenReturn(new NotionMarkdown("본문", false, List.of()));

		assertEquals("본문", service.importPage(7L, "page-1").content());
		assertEquals(1L, connection.getCredentialRevision());
		verify(client).refresh("access-token");
	}

	@Test
	void sharesOneTwentySecondBudgetAcrossPageThenMarkdownCalls() {
		MutableClock requestClock = new MutableClock(Instant.parse("2026-09-01T00:00:00Z"));
		BudgetObservingClient budgetClient = new BudgetObservingClient(requestClock);
		NotionConnection connection = NotionConnection.connected(
			7L, "workspace-a", null,
			new EncryptedToken(new byte[]{1}, new byte[12], "v1"), null, requestClock.instant()
		);
		when(connections.findByUserIdForUpdate(7L)).thenReturn(Optional.of(connection));
		NotionConnectionService budgeted = new NotionConnectionService(
			connections, states, budgetClient, cipher, users, new NotionMarkdownProcessor(), requestClock,
			List.of("https://app.openmd.example/learning/import/notion"),
			"https://app.openmd.example/learning/import/notion"
		);

		assertEquals("본문", budgeted.importPage(7L, "page-1").content());
		assertEquals(List.of(Duration.ofSeconds(15), Duration.ofSeconds(8)), budgetClient.observed);
	}

	private static final class MutableClock extends Clock {
		private Instant instant;
		private MutableClock(Instant instant) { this.instant = instant; }
		void advance(Duration duration) { instant = instant.plus(duration); }
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return instant; }
	}

	private static final class BudgetObservingClient implements NotionClient {
		private final MutableClock clock;
		private final List<Duration> observed = new ArrayList<>();
		private BudgetObservingClient(MutableClock clock) { this.clock = clock; }
		@Override public NotionPage retrievePage(String token, String pageId) {
			observed.add(com.openmd.server.integration.notion.client.NotionRequestBudget.remaining(
				clock, Duration.ofSeconds(15)
			));
			clock.advance(Duration.ofSeconds(12));
			return new NotionPage("제목");
		}
		@Override public NotionMarkdown retrieveMarkdown(String token, String pageId) {
			observed.add(com.openmd.server.integration.notion.client.NotionRequestBudget.remaining(
				clock, Duration.ofSeconds(15)
			));
			return new NotionMarkdown("본문", false, List.of());
		}
		@Override public String authorizationUrl(String state) { throw new UnsupportedOperationException(); }
		@Override public NotionTokenGrant exchangeAuthorizationCode(String code) { throw new UnsupportedOperationException(); }
		@Override public NotionTokenGrant refresh(String refreshToken) { throw new UnsupportedOperationException(); }
		@Override public boolean revoke(String accessToken) { throw new UnsupportedOperationException(); }
		@Override public boolean introspect(String accessToken) { throw new UnsupportedOperationException(); }
		@Override public com.openmd.server.integration.notion.client.NotionPageSearch searchPages(
			String accessToken, String cursor, String query) { throw new UnsupportedOperationException(); }
	}
}
