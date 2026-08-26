package com.openmd.server.notion.security;

public interface NotionTokenCipher {

	EncryptedNotionToken encrypt(String plaintext, NotionTokenContext context);

	String decrypt(EncryptedNotionToken encrypted, NotionTokenContext context);
}
