package com.openmd.server.integration.notion.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AesGcmTokenCipherTest {

	private final AesGcmTokenCipher cipher = new AesGcmTokenCipher(
		Map.of("v1", new byte[32]), "v1", new SecureRandom()
	);

	@Test
	void encryptsWithFreshNonceAndBindsCiphertextToOwnerWorkspaceAndPurpose() {
		EncryptedToken first = cipher.encrypt(7L, "workspace-a", TokenType.ACCESS, "secret-token");
		EncryptedToken second = cipher.encrypt(7L, "workspace-a", TokenType.ACCESS, "secret-token");

		assertEquals("secret-token", cipher.decrypt(7L, "workspace-a", TokenType.ACCESS, first));
		assertFalse(java.util.Arrays.equals(first.nonce(), second.nonce()));
		assertThrows(TokenDecryptionException.class,
			() -> cipher.decrypt(8L, "workspace-a", TokenType.ACCESS, first));
		assertThrows(TokenDecryptionException.class,
			() -> cipher.decrypt(7L, "workspace-a", TokenType.REFRESH, first));
	}

	@Test
	void rejectsUnknownKeyVersionAndTamperedTag() {
		EncryptedToken encrypted = cipher.encrypt(7L, "workspace-a", TokenType.ACCESS, "secret-token");
		byte[] tampered = encrypted.ciphertext().clone();
		tampered[tampered.length - 1] ^= 1;

		assertThrows(TokenDecryptionException.class, () -> cipher.decrypt(
			7L, "workspace-a", TokenType.ACCESS,
			new EncryptedToken(tampered, encrypted.nonce(), encrypted.keyVersion())
		));
		assertThrows(TokenDecryptionException.class, () -> cipher.decrypt(
			7L, "workspace-a", TokenType.ACCESS,
			new EncryptedToken(encrypted.ciphertext(), encrypted.nonce(), "missing")
		));
	}
}
