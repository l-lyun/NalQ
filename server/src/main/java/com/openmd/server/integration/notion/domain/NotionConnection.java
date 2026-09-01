package com.openmd.server.integration.notion.domain;

import com.openmd.server.integration.notion.crypto.EncryptedToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notion_connections")
public class NotionConnection {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "user_id", nullable = false, unique = true)
	private Long userId;
	@Column(name = "workspace_id", nullable = false, length = 36)
	private String workspaceId;
	@Column(name = "workspace_name", length = 255)
	private String workspaceName;
	@Column(name = "access_token_ciphertext", nullable = false, columnDefinition = "TEXT")
	private String accessTokenCiphertext;
	@Column(name = "access_token_nonce", nullable = false, columnDefinition = "BINARY(12)")
	private byte[] accessTokenNonce;
	@Column(name = "refresh_token_ciphertext", columnDefinition = "TEXT")
	private String refreshTokenCiphertext;
	@Column(name = "refresh_token_nonce", columnDefinition = "BINARY(12)")
	private byte[] refreshTokenNonce;
	@Column(name = "encryption_key_version", nullable = false, length = 32)
	private String encryptionKeyVersion;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private NotionConnectionStatus status;
	@Column(name = "credential_revision", nullable = false)
	private long credentialRevision;
	@Column(name = "connected_at", nullable = false)
	private Instant connectedAt;
	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected NotionConnection() {}

	public static NotionConnection connected(long userId, String workspaceId, String workspaceName,
		EncryptedToken access, EncryptedToken refresh, Instant now) {
		NotionConnection connection = new NotionConnection();
		connection.userId = userId;
		connection.workspaceId = workspaceId;
		connection.workspaceName = workspaceName;
		connection.replaceTokens(access, refresh, now, false);
		connection.status = NotionConnectionStatus.CONNECTED;
		connection.credentialRevision = 0;
		connection.connectedAt = now;
		connection.createdAt = now;
		connection.updatedAt = now;
		return connection;
	}

	public void reauthorize(String workspaceName, EncryptedToken access, EncryptedToken refresh, Instant now) {
		this.workspaceName = workspaceName;
		replaceTokens(access, refresh, now, true);
		status = NotionConnectionStatus.CONNECTED;
	}

	public void markReauthRequired(Instant now) {
		status = NotionConnectionStatus.REAUTH_REQUIRED;
		updatedAt = now;
	}

	private void replaceTokens(EncryptedToken access, EncryptedToken refresh, Instant now, boolean bumpRevision) {
		accessTokenCiphertext = java.util.Base64.getEncoder().encodeToString(access.ciphertext());
		accessTokenNonce = access.nonce();
		encryptionKeyVersion = access.keyVersion();
		refreshTokenCiphertext = refresh == null ? null
			: java.util.Base64.getEncoder().encodeToString(refresh.ciphertext());
		refreshTokenNonce = refresh == null ? null : refresh.nonce();
		if (refresh != null && !access.keyVersion().equals(refresh.keyVersion())) {
			throw new IllegalArgumentException("Access and refresh tokens must use the same key version");
		}
		if (bumpRevision) credentialRevision++;
		updatedAt = now;
	}

	public EncryptedToken accessToken() { return new EncryptedToken(java.util.Base64.getDecoder().decode(accessTokenCiphertext), accessTokenNonce, encryptionKeyVersion); }
	public EncryptedToken refreshToken() { return refreshTokenCiphertext == null ? null : new EncryptedToken(java.util.Base64.getDecoder().decode(refreshTokenCiphertext), refreshTokenNonce, encryptionKeyVersion); }
	public Long getUserId() { return userId; }
	public String getWorkspaceId() { return workspaceId; }
	public String getWorkspaceName() { return workspaceName; }
	public long getCredentialRevision() { return credentialRevision; }
	public boolean isReauthRequired() { return status == NotionConnectionStatus.REAUTH_REQUIRED; }
	public NotionConnectionStatus getStatus() { return status; }
}
