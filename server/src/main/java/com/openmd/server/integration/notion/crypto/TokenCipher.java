package com.openmd.server.integration.notion.crypto;

public interface TokenCipher {
	EncryptedToken encrypt(long userId, String workspaceId, TokenType type, String plaintext);

	String decrypt(long userId, String workspaceId, TokenType type, EncryptedToken encrypted);
}
