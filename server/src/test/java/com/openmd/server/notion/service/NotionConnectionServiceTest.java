package com.openmd.server.notion.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.notion.domain.NotionConnection;
import com.openmd.server.notion.dto.model.NotionOAuthGrant;
import com.openmd.server.notion.dto.model.NotionOAuthState;
import com.openmd.server.notion.dto.response.NotionAuthorization;
import com.openmd.server.notion.integration.NotionOAuthPort;
import com.openmd.server.notion.integration.NotionOAuthException;
import com.openmd.server.notion.repository.NotionConnectionRepository;
import com.openmd.server.notion.repository.NotionOAuthStateStore;
import com.openmd.server.notion.security.EncryptedNotionToken;
import com.openmd.server.notion.security.NotionTokenCipher;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotionConnectionServiceTest {

	private final NotionConnectionRepository connections = mock(NotionConnectionRepository.class);
	private final NotionOAuthStateStore states = mock(NotionOAuthStateStore.class);
	private final NotionOAuthPort oauth = mock(NotionOAuthPort.class);
	private final NotionTokenCipher cipher = mock(NotionTokenCipher.class);
	private NotionConnectionService service;

	@BeforeEach
	void setUp() {
		service = new NotionConnectionService(
			connections, states, oauth, cipher,
			"client-id", URI.create("http://localhost:8080/api/v1/notion/oauth/callback"),
			URI.create("http://localhost:5173/learning"), Duration.ofMinutes(10)
		);
	}

	@Test
	void startsAuthorizationWithOneTimeStateAndAConfiguredRedirect() {
		NotionAuthorization result = service.startAuthorization(7L);

		assertTrue(result.authorizationUrl().startsWith("https://api.notion.com/v1/oauth/authorize?"));
		assertTrue(result.authorizationUrl().contains("client_id=client-id"));
		assertTrue(result.authorizationUrl().contains("response_type=code"));
		verify(states).save(any(String.class), any(NotionOAuthState.class), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10)));
	}

	@Test
	void callbackConsumesStateAndStoresBothEncryptedTokensForThatUser() {
		when(states.consume(any())).thenReturn(Optional.of(new NotionOAuthState(7L)));
		when(oauth.exchangeAuthorizationCode("code-1")).thenReturn(new NotionOAuthGrant(
			"access", "refresh", "bot-1", "workspace-1", "팀 공간", null
		));
		when(cipher.encrypt(any(), any()))
			.thenReturn(new EncryptedNotionToken("v1", new byte[] {1}))
			.thenReturn(new EncryptedNotionToken("v1", new byte[] {2}));

		URI redirect = service.completeAuthorization("code-1", "state-1");

		assertEquals("http://localhost:5173/learning?notion=connected", redirect.toString());
		verify(connections).save(any(NotionConnection.class));
	}

	@Test
	void reportsNoConnectionAsNormalStateAndDisconnectIsIdempotent() {
		when(connections.findByUserId(7L)).thenReturn(Optional.empty());

		assertFalse(service.getConnection(7L).connected());
		service.disconnect(7L);

		verify(oauth, never()).revoke(any());
		verify(connections, never()).delete(any());
	}

	@Test
	void refreshesBothTokensWhileHoldingTheUsersConnectionLock() {
		NotionOAuthGrant original = new NotionOAuthGrant(
			"access-old", "refresh-old", "bot-1", "workspace-1", "팀 공간", null
		);
		NotionConnection connection = NotionConnection.connected(
			7L, original,
			new EncryptedNotionToken("v1", new byte[] {1}),
			new EncryptedNotionToken("v1", new byte[] {2})
		);
		when(connections.findByUserIdForUpdate(7L)).thenReturn(Optional.of(connection));
		when(cipher.decrypt(any(), any())).thenReturn("refresh-old");
		when(oauth.refresh("refresh-old")).thenReturn(new NotionOAuthGrant(
			"access-new", "refresh-new", "bot-1", "workspace-1", "팀 공간", null
		));
		when(cipher.encrypt(any(), any()))
			.thenReturn(new EncryptedNotionToken("v2", new byte[] {3}))
			.thenReturn(new EncryptedNotionToken("v2", new byte[] {4}));

		assertEquals("access-new", service.refreshAccessToken(7L));
		assertEquals("v2", connection.getEncryptionKeyVersion());
		assertEquals(3, connection.getAccessTokenCiphertext()[0]);
		assertEquals(4, connection.getRefreshTokenCiphertext()[0]);
	}

	@Test
	void marksTheConnectionForReauthenticationWhenRefreshCredentialsAreRejected() {
		NotionConnection connection = NotionConnection.connected(
			7L,
			new NotionOAuthGrant("access", "refresh", "bot-1", "workspace-1", null, null),
			new EncryptedNotionToken("v1", new byte[] {1}),
			new EncryptedNotionToken("v1", new byte[] {2})
		);
		when(connections.findByUserIdForUpdate(7L)).thenReturn(Optional.of(connection));
		when(cipher.decrypt(any(), any())).thenReturn("refresh");
		when(oauth.refresh("refresh")).thenThrow(new NotionOAuthException(true));

		org.junit.jupiter.api.Assertions.assertThrows(
			NotionOAuthException.class, () -> service.refreshAccessToken(7L)
		);
		assertEquals(com.openmd.server.notion.domain.NotionConnectionStatus.REAUTH_REQUIRED, connection.getStatus());
	}
}
