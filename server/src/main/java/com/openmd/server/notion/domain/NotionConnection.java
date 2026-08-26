package com.openmd.server.notion.domain;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.notion.dto.model.NotionOAuthGrant;
import com.openmd.server.notion.security.EncryptedNotionToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Arrays;

@Entity
@Table(
	name = "notion_connections",
	uniqueConstraints = @UniqueConstraint(name = "uk_notion_connections_user", columnNames = "user_id")
)
public class NotionConnection extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private long userId;

	@Column(name = "workspace_id", nullable = false, length = 64)
	private String workspaceId;

	@Column(name = "workspace_name", length = 255)
	private String workspaceName;

	@Column(name = "workspace_icon_url", length = 2048)
	private String workspaceIconUrl;

	@Column(name = "bot_id", nullable = false, length = 64)
	private String botId;

	@Column(name = "access_token_ciphertext", nullable = false, length = 4096)
	private byte[] accessTokenCiphertext;

	@Column(name = "refresh_token_ciphertext", nullable = false, length = 4096)
	private byte[] refreshTokenCiphertext;

	@Column(name = "encryption_key_version", nullable = false, length = 32)
	private String encryptionKeyVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private NotionConnectionStatus status;

	protected NotionConnection() {
	}

	public static NotionConnection connected(
		long userId,
		NotionOAuthGrant grant,
		EncryptedNotionToken accessToken,
		EncryptedNotionToken refreshToken
	) {
		NotionConnection connection = new NotionConnection();
		connection.userId = userId;
		connection.reconnect(grant, accessToken, refreshToken);
		return connection;
	}

	public void reconnect(
		NotionOAuthGrant grant,
		EncryptedNotionToken accessToken,
		EncryptedNotionToken refreshToken
	) {
		if (!accessToken.keyVersion().equals(refreshToken.keyVersion())) {
			throw new IllegalArgumentException("Notion token key versions must match");
		}
		workspaceId = grant.workspaceId();
		workspaceName = grant.workspaceName();
		workspaceIconUrl = grant.workspaceIconUrl();
		botId = grant.botId();
		accessTokenCiphertext = accessToken.ciphertext();
		refreshTokenCiphertext = refreshToken.ciphertext();
		encryptionKeyVersion = accessToken.keyVersion();
		status = NotionConnectionStatus.CONNECTED;
	}

	public void replaceTokens(EncryptedNotionToken accessToken, EncryptedNotionToken refreshToken) {
		if (!accessToken.keyVersion().equals(refreshToken.keyVersion())) {
			throw new IllegalArgumentException("Notion token key versions must match");
		}
		accessTokenCiphertext = accessToken.ciphertext();
		refreshTokenCiphertext = refreshToken.ciphertext();
		encryptionKeyVersion = accessToken.keyVersion();
		status = NotionConnectionStatus.CONNECTED;
	}

	public void requireReauthentication() {
		status = NotionConnectionStatus.REAUTH_REQUIRED;
	}

	public long getUserId() { return userId; }
	public String getWorkspaceId() { return workspaceId; }
	public String getWorkspaceName() { return workspaceName; }
	public String getWorkspaceIconUrl() { return workspaceIconUrl; }
	public String getBotId() { return botId; }
	public String getEncryptionKeyVersion() { return encryptionKeyVersion; }
	public NotionConnectionStatus getStatus() { return status; }
	public byte[] getAccessTokenCiphertext() { return Arrays.copyOf(accessTokenCiphertext, accessTokenCiphertext.length); }
	public byte[] getRefreshTokenCiphertext() { return Arrays.copyOf(refreshTokenCiphertext, refreshTokenCiphertext.length); }
}
