package com.openmd.server.integration.notion.service;

import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.global.api.FieldError;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.integration.notion.client.NotionClient;
import com.openmd.server.integration.notion.client.NotionClientException;
import com.openmd.server.integration.notion.client.NotionClientFailure;
import com.openmd.server.integration.notion.client.NotionPageSearch;
import com.openmd.server.integration.notion.client.NotionRequestBudget;
import com.openmd.server.integration.notion.client.NotionTokenGrant;
import com.openmd.server.integration.notion.crypto.EncryptedToken;
import com.openmd.server.integration.notion.crypto.TokenCipher;
import com.openmd.server.integration.notion.crypto.TokenType;
import com.openmd.server.integration.notion.domain.NotionConnection;
import com.openmd.server.integration.notion.dto.model.NotionOAuthState;
import com.openmd.server.integration.notion.dto.response.NotionAuthorization;
import com.openmd.server.integration.notion.dto.response.NotionConnectionView;
import com.openmd.server.integration.notion.dto.response.NotionDisconnected;
import com.openmd.server.integration.notion.dto.response.NotionImportedPage;
import com.openmd.server.integration.notion.dto.response.NotionPageList;
import com.openmd.server.integration.notion.error.NotionErrorCode;
import com.openmd.server.integration.notion.repository.NotionConnectionRepository;
import com.openmd.server.integration.notion.repository.NotionOAuthStateStore;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.transaction.annotation.Transactional;

public class NotionConnectionService {

	private static final Duration STATE_TTL = Duration.ofMinutes(15);
	private final NotionConnectionRepository connections;
	private final NotionOAuthStateStore states;
	private final NotionClient client;
	private final TokenCipher cipher;
	private final UserRepository users;
	private final NotionMarkdownProcessor markdown;
	private final Clock clock;
	private final List<String> allowedReturnUris;
	private final String failureReturnUri;
	private final SecureRandom random = new SecureRandom();

	public NotionConnectionService(
		NotionConnectionRepository connections,
		NotionOAuthStateStore states,
		NotionClient client,
		TokenCipher cipher,
		UserRepository users,
		NotionMarkdownProcessor markdown,
		Clock clock,
		List<String> allowedReturnUris,
		String failureReturnUri
	) {
		this.connections = connections;
		this.states = states;
		this.client = client;
		this.cipher = cipher;
		this.users = users;
		this.markdown = markdown;
		this.clock = clock;
		this.allowedReturnUris = List.copyOf(allowedReturnUris);
		this.failureReturnUri = failureReturnUri;
	}

	@Transactional(readOnly = true)
	public NotionConnectionView connection(long userId) {
		return connections.findByUserId(userId)
			.map(value -> new NotionConnectionView(value.getStatus().name(), value.getWorkspaceName()))
			.orElseGet(() -> new NotionConnectionView("DISCONNECTED", null));
	}

	@Transactional(readOnly = true)
	public NotionAuthorization startAuthorization(long userId, String returnUri) {
		if (returnUri == null || !allowedReturnUris.contains(returnUri)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT,
				List.of(new FieldError("returnUri", "등록된 복귀 URI여야 합니다.")));
		}
		Optional<NotionConnection> current = connections.findByUserId(userId);
		String rawState = newState();
		Instant now = clock.instant();
		states.save(rawState, new NotionOAuthState(
			userId,
			returnUri,
			current.isPresent() ? "REAUTHORIZE" : "CONNECT",
			now,
			current.map(NotionConnection::getWorkspaceId).orElse(null),
			current.map(NotionConnection::getCredentialRevision).orElse(null)
		), STATE_TTL);
		return new NotionAuthorization(client.authorizationUrl(rawState), now.plus(STATE_TTL));
	}

	@Transactional
	public String completeAuthorization(String rawState, String code, String providerError) {
		return NotionRequestBudget.within(clock,
			() -> completeAuthorizationWithinBudget(rawState, code, providerError));
	}

	private String completeAuthorizationWithinBudget(String rawState, String code, String providerError) {
		Optional<NotionOAuthState> located = rawState == null ? Optional.empty() : states.find(rawState);
		if (located.isEmpty()) {
			return failed(failureReturnUri, NotionErrorCode.CONNECTION_REQUIRED);
		}
		NotionOAuthState state = located.get();
		if ("access_denied".equals(providerError)) {
			if (states.consume(rawState).isEmpty()) {
				return failed(failureReturnUri, NotionErrorCode.CONNECTION_REQUIRED);
			}
			return append(state.returnUri(), "outcome=cancelled");
		}
		if (providerError != null || code == null) {
			return states.consume(rawState).isPresent()
				? failed(state.returnUri(), NotionErrorCode.CONNECTION_REQUIRED)
				: failed(failureReturnUri, NotionErrorCode.CONNECTION_REQUIRED);
		}
		// A user row always exists for an authenticated state and remains a stable lock target even
		// before the first notion_connections row exists. This serializes concurrent CONNECT callbacks.
		if (users.findByIdForUpdate(state.userId()).isEmpty()) {
			return failed(state.returnUri(), NotionErrorCode.CONNECTION_REQUIRED);
		}
		Optional<NotionOAuthState> consumed = states.consume(rawState);
		if (consumed.isEmpty() || !consumed.get().equals(state)) {
			return failed(state.returnUri(), NotionErrorCode.CONNECTION_REQUIRED);
		}
		Optional<NotionConnection> current = connections.findByUserIdForUpdate(state.userId());
		if (!snapshotStillValid(state, current)) {
			return failed(state.returnUri(), NotionErrorCode.CONNECTION_REQUIRED);
		}

		NotionTokenGrant grant;
		try {
			grant = client.exchangeAuthorizationCode(code);
		} catch (NotionClientException exception) {
			return failed(state.returnUri(), NotionErrorCode.TEMPORARILY_UNAVAILABLE);
		}
		if (current.isPresent() && !current.get().getWorkspaceId().equals(grant.workspaceId())) {
			try { client.revoke(grant.accessToken()); } catch (RuntimeException ignored) { }
			return failed(state.returnUri(), NotionErrorCode.WORKSPACE_MISMATCH);
		}

		EncryptedToken access = cipher.encrypt(state.userId(), grant.workspaceId(), TokenType.ACCESS, grant.accessToken());
		EncryptedToken refresh = grant.refreshToken() == null ? null
			: cipher.encrypt(state.userId(), grant.workspaceId(), TokenType.REFRESH, grant.refreshToken());
		if (current.isEmpty()) {
			connections.save(NotionConnection.connected(
				state.userId(), grant.workspaceId(), grant.workspaceName(), access, refresh, clock.instant()
			));
		} else {
			current.get().reauthorize(grant.workspaceName(), access, refresh, clock.instant());
		}
		return append(state.returnUri(), "outcome=connected");
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public NotionPageList pages(long userId, String cursor, String query) {
		return NotionRequestBudget.within(clock, () -> pagesWithinBudget(userId, cursor, query));
	}

	private NotionPageList pagesWithinBudget(long userId, String cursor, String query) {
		NotionConnection connection = requiredLockedConnection(userId);
		NotionPageSearch result = callWithOneRefresh(connection,
			token -> client.searchPages(token, emptyToNull(cursor), emptyToNull(query)));
		return new NotionPageList(result.items(), result.nextCursor());
	}

	@Transactional(noRollbackFor = BusinessException.class)
	public NotionImportedPage importPage(long userId, String pageId) {
		return NotionRequestBudget.within(clock, () -> importPageWithinBudget(userId, pageId));
	}

	private NotionImportedPage importPageWithinBudget(long userId, String pageId) {
		if (pageId == null || pageId.isBlank()) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT,
				List.of(new FieldError("pageId", "pageId가 필요합니다.")));
		}
		NotionConnection connection = requiredLockedConnection(userId);
		return callWithOneRefresh(connection, token -> {
			String title = client.retrievePage(token, pageId).title();
			String content = markdown.process(client.retrieveMarkdown(token, pageId));
			return new NotionImportedPage("NOTION", title == null ? "" : title, content);
		});
	}

	@Transactional
	public NotionDisconnected disconnect(long userId) {
		return NotionRequestBudget.within(clock, () -> disconnectWithinBudget(userId));
	}

	private NotionDisconnected disconnectWithinBudget(long userId) {
		if (users.findByIdForUpdate(userId).isEmpty()) {
			states.invalidateUser(userId);
			return new NotionDisconnected("DISCONNECTED");
		}
		Optional<NotionConnection> found = connections.findByUserIdForUpdate(userId);
		if (found.isEmpty()) {
			states.invalidateUser(userId);
			return new NotionDisconnected("DISCONNECTED");
		}
		NotionConnection connection = found.get();
		String token = accessToken(connection);
		boolean revoked;
		try {
			revoked = client.revoke(token);
		} catch (NotionClientException exception) {
			revoked = false;
		}
		if (!revoked) {
			try {
				if (client.introspect(token)) {
					throw new BusinessException(NotionErrorCode.TEMPORARILY_UNAVAILABLE);
				}
			} catch (BusinessException exception) {
				throw exception;
			} catch (RuntimeException exception) {
				throw new BusinessException(NotionErrorCode.TEMPORARILY_UNAVAILABLE);
			}
		}
		connections.delete(connection);
		states.invalidateUser(userId);
		return new NotionDisconnected("DISCONNECTED");
	}

	private <T> T callWithOneRefresh(NotionConnection connection, Function<String, T> operation) {
		if (connection.isReauthRequired()) {
			throw new BusinessException(NotionErrorCode.REAUTH_REQUIRED);
		}
		try {
			return operation.apply(accessToken(connection));
		} catch (NotionClientException first) {
			if (first.failure() != NotionClientFailure.UNAUTHORIZED) {
				throw mapped(first);
			}
		}
		EncryptedToken encryptedRefresh = connection.refreshToken();
		if (encryptedRefresh == null) {
			connection.markReauthRequired(clock.instant());
			throw new BusinessException(NotionErrorCode.REAUTH_REQUIRED);
		}
		String refreshToken = cipher.decrypt(
			connection.getUserId(), connection.getWorkspaceId(), TokenType.REFRESH, encryptedRefresh
		);
		NotionTokenGrant refreshed;
		try {
			refreshed = client.refresh(refreshToken);
		} catch (NotionClientException exception) {
			if (exception.failure() == NotionClientFailure.INVALID_GRANT
				|| exception.failure() == NotionClientFailure.UNAUTHORIZED) {
				connection.markReauthRequired(clock.instant());
				throw new BusinessException(NotionErrorCode.REAUTH_REQUIRED);
			}
			throw new BusinessException(NotionErrorCode.TEMPORARILY_UNAVAILABLE);
		}
		if (refreshed.workspaceId() != null && !connection.getWorkspaceId().equals(refreshed.workspaceId())) {
			connection.markReauthRequired(clock.instant());
			throw new BusinessException(NotionErrorCode.REAUTH_REQUIRED);
		}
		String newRefresh = refreshed.refreshToken() == null ? refreshToken : refreshed.refreshToken();
		EncryptedToken access = cipher.encrypt(connection.getUserId(), connection.getWorkspaceId(),
			TokenType.ACCESS, refreshed.accessToken());
		EncryptedToken refresh = cipher.encrypt(connection.getUserId(), connection.getWorkspaceId(),
			TokenType.REFRESH, newRefresh);
		connection.reauthorize(connection.getWorkspaceName(), access, refresh, clock.instant());
		try {
			return operation.apply(refreshed.accessToken());
		} catch (NotionClientException second) {
			if (second.failure() == NotionClientFailure.UNAUTHORIZED) {
				connection.markReauthRequired(clock.instant());
				throw new BusinessException(NotionErrorCode.REAUTH_REQUIRED);
			}
			throw mapped(second);
		}
	}

	private NotionConnection requiredLockedConnection(long userId) {
		return connections.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new BusinessException(NotionErrorCode.CONNECTION_REQUIRED));
	}

	private String accessToken(NotionConnection connection) {
		return cipher.decrypt(connection.getUserId(), connection.getWorkspaceId(), TokenType.ACCESS,
			connection.accessToken());
	}

	private BusinessException mapped(NotionClientException exception) {
		return switch (exception.failure()) {
			case NOT_ACCESSIBLE -> new BusinessException(NotionErrorCode.PAGE_NOT_ACCESSIBLE);
			case UNAUTHORIZED, INVALID_GRANT -> new BusinessException(NotionErrorCode.REAUTH_REQUIRED);
			case TEMPORARY -> new BusinessException(NotionErrorCode.TEMPORARILY_UNAVAILABLE);
		};
	}

	private boolean snapshotStillValid(NotionOAuthState state, Optional<NotionConnection> current) {
		if ("CONNECT".equals(state.intent())) return current.isEmpty();
		return current.isPresent()
			&& current.get().getWorkspaceId().equals(state.workspaceId())
			&& current.get().getCredentialRevision() == state.credentialRevision();
	}

	private String newState() {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static String emptyToNull(String value) {
		return value == null || value.strip().isEmpty() ? null : value.strip();
	}

	private static String failed(String returnUri, NotionErrorCode code) {
		return append(returnUri, "outcome=failed&error=" + code.code());
	}

	private static String append(String uri, String query) {
		return uri + (uri.contains("?") ? "&" : "?") + query;
	}
}
