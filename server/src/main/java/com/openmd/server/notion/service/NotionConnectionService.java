package com.openmd.server.notion.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.notion.domain.NotionConnection;
import com.openmd.server.notion.dto.model.NotionOAuthGrant;
import com.openmd.server.notion.dto.model.NotionOAuthState;
import com.openmd.server.notion.dto.response.NotionAuthorization;
import com.openmd.server.notion.dto.response.NotionConnectionView;
import com.openmd.server.notion.integration.NotionOAuthException;
import com.openmd.server.notion.integration.NotionOAuthPort;
import com.openmd.server.notion.repository.NotionConnectionRepository;
import com.openmd.server.notion.repository.NotionOAuthStateStore;
import com.openmd.server.notion.security.EncryptedNotionToken;
import com.openmd.server.notion.security.NotionTokenCipher;
import com.openmd.server.notion.security.NotionTokenContext;
import com.openmd.server.notion.security.NotionTokenKind;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

public class NotionConnectionService {

	private final NotionConnectionRepository connections;
	private final NotionOAuthStateStore states;
	private final NotionOAuthPort oauth;
	private final NotionTokenCipher cipher;
	private final String clientId;
	private final URI redirectUri;
	private final URI frontendReturnUri;
	private final Duration stateTtl;
	private final SecureRandom random = new SecureRandom();

	public NotionConnectionService(
		NotionConnectionRepository connections,
		NotionOAuthStateStore states,
		NotionOAuthPort oauth,
		NotionTokenCipher cipher,
		String clientId,
		URI redirectUri,
		URI frontendReturnUri,
		Duration stateTtl
	) {
		this.connections = connections;
		this.states = states;
		this.oauth = oauth;
		this.cipher = cipher;
		this.clientId = clientId;
		this.redirectUri = redirectUri;
		this.frontendReturnUri = frontendReturnUri;
		this.stateTtl = stateTtl;
	}

	public NotionAuthorization startAuthorization(long userId) {
		byte[] randomBytes = new byte[32];
		random.nextBytes(randomBytes);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		states.save(digest(state), new NotionOAuthState(userId), stateTtl);
		String authorizationUrl = UriComponentsBuilder
			.fromUriString("https://api.notion.com/v1/oauth/authorize")
			.queryParam("client_id", clientId)
			.queryParam("redirect_uri", redirectUri)
			.queryParam("response_type", "code")
			.queryParam("owner", "user")
			.queryParam("state", state)
			.build()
			.encode()
			.toUriString();
		return new NotionAuthorization(authorizationUrl);
	}

	@Transactional
	public URI completeAuthorization(String code, String state) {
		if (code == null || code.isBlank() || state == null || state.isBlank()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT);
		}
		NotionOAuthState storedState = states.consume(digest(state))
			.orElseThrow(() -> new BusinessException(CommonErrorCode.INVALID_INPUT));
		NotionOAuthGrant grant = oauth.exchangeAuthorizationCode(code);
		EncryptedNotionToken access = cipher.encrypt(
			grant.accessToken(), new NotionTokenContext(storedState.userId(), grant.workspaceId(), NotionTokenKind.ACCESS)
		);
		EncryptedNotionToken refresh = cipher.encrypt(
			grant.refreshToken(), new NotionTokenContext(storedState.userId(), grant.workspaceId(), NotionTokenKind.REFRESH)
		);
		Optional<NotionConnection> existing = connections.findByUserId(storedState.userId());
		NotionConnection connection = existing
			.orElseGet(() -> NotionConnection.connected(storedState.userId(), grant, access, refresh));
		if (existing.isPresent()) {
			connection.reconnect(grant, access, refresh);
		}
		connections.save(connection);
		return frontendRedirect("connected");
	}

	public URI cancelAuthorization(String state) {
		if (state == null || state.isBlank() || states.consume(digest(state)).isEmpty()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT);
		}
		return frontendRedirect("cancelled");
	}

	@Transactional(readOnly = true)
	public NotionConnectionView getConnection(long userId) {
		return connections.findByUserId(userId)
			.map(connection -> new NotionConnectionView(
				connection.getStatus() == com.openmd.server.notion.domain.NotionConnectionStatus.CONNECTED,
				connection.getStatus(),
				connection.getWorkspaceId(),
				connection.getWorkspaceName(),
				connection.getWorkspaceIconUrl()
			))
			.orElseGet(NotionConnectionView::disconnected);
	}

	@Transactional
	public void disconnect(long userId) {
		Optional<NotionConnection> optional = connections.findByUserIdForUpdate(userId);
		if (optional.isEmpty()) {
			return;
		}
		NotionConnection connection = optional.get();
		try {
			oauth.revoke(decrypt(connection, NotionTokenKind.ACCESS));
		} catch (RuntimeException ignored) {
			// Local credentials must be deleted even when the remote revoke endpoint is unavailable.
		}
		connections.delete(connection);
	}

	@Transactional(noRollbackFor = NotionOAuthException.class)
	public String refreshAccessToken(long userId) {
		NotionConnection connection = connections.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
		try {
			NotionOAuthGrant refreshed = oauth.refresh(decrypt(connection, NotionTokenKind.REFRESH));
			EncryptedNotionToken access = cipher.encrypt(
				refreshed.accessToken(), tokenContext(connection, NotionTokenKind.ACCESS)
			);
			EncryptedNotionToken refresh = cipher.encrypt(
				refreshed.refreshToken(), tokenContext(connection, NotionTokenKind.REFRESH)
			);
			connection.replaceTokens(access, refresh);
			return refreshed.accessToken();
		} catch (NotionOAuthException exception) {
			if (exception.isReauthenticationRequired()) {
				connection.requireReauthentication();
			}
			throw exception;
		}
	}

	private String decrypt(NotionConnection connection, NotionTokenKind kind) {
		byte[] ciphertext = kind == NotionTokenKind.ACCESS
			? connection.getAccessTokenCiphertext()
			: connection.getRefreshTokenCiphertext();
		return cipher.decrypt(
			new EncryptedNotionToken(connection.getEncryptionKeyVersion(), ciphertext),
			tokenContext(connection, kind)
		);
	}

	private NotionTokenContext tokenContext(NotionConnection connection, NotionTokenKind kind) {
		return new NotionTokenContext(connection.getUserId(), connection.getWorkspaceId(), kind);
	}

	private URI frontendRedirect(String result) {
		return UriComponentsBuilder.fromUri(frontendReturnUri)
			.replaceQueryParam("notion", result)
			.build(true)
			.toUri();
	}

	private String digest(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}
}
