package com.openmd.server.notion.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AesGcmNotionTokenCipherTest {

	private static final byte[] KEY = Base64.getDecoder().decode(
		"MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
	);

	@Test
	void encryptsWithTheActiveVersionAndRejectsWrongContextOrTampering() {
		AesGcmNotionTokenCipher cipher = new AesGcmNotionTokenCipher("v1", Map.of("v1", KEY));
		NotionTokenContext context = new NotionTokenContext(7L, "workspace-1", NotionTokenKind.ACCESS);

		EncryptedNotionToken encrypted = cipher.encrypt("secret-access-token", context);

		assertEquals("v1", encrypted.keyVersion());
		assertFalse(new String(encrypted.ciphertext()).contains("secret-access-token"));
		assertEquals("secret-access-token", cipher.decrypt(encrypted, context));
		assertThrows(IllegalStateException.class, () -> cipher.decrypt(
			encrypted,
			new NotionTokenContext(8L, "workspace-1", NotionTokenKind.ACCESS)
		));

		byte[] tampered = encrypted.ciphertext().clone();
		tampered[tampered.length - 1] ^= 1;
		assertThrows(IllegalStateException.class, () -> cipher.decrypt(
			new EncryptedNotionToken(encrypted.keyVersion(), tampered), context
		));
	}

	@Test
	void requiresA256BitKey() {
		assertThrows(IllegalArgumentException.class, () ->
			new AesGcmNotionTokenCipher("v1", Map.of("v1", new byte[16])));
	}
}
