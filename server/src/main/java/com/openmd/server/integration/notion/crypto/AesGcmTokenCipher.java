package com.openmd.server.integration.notion.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmTokenCipher implements TokenCipher {

	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;
	private final Map<String, byte[]> keys;
	private final String writeVersion;
	private final SecureRandom random;

	public AesGcmTokenCipher(Map<String, byte[]> keys, String writeVersion, SecureRandom random) {
		Objects.requireNonNull(keys);
		this.keys = keys.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
			Map.Entry::getKey,
			entry -> validateKey(entry.getKey(), entry.getValue())
		));
		if (!this.keys.containsKey(writeVersion)) {
			throw new IllegalArgumentException("Notion token write key version is missing");
		}
		this.writeVersion = writeVersion;
		this.random = Objects.requireNonNull(random);
	}

	@Override
	public EncryptedToken encrypt(long userId, String workspaceId, TokenType type, String plaintext) {
		byte[] nonce = new byte[NONCE_BYTES];
		random.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys.get(writeVersion), "AES"),
				new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(aad(userId, workspaceId, type));
			return new EncryptedToken(
				cipher.doFinal(Objects.requireNonNull(plaintext).getBytes(StandardCharsets.UTF_8)), nonce, writeVersion
			);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Could not encrypt Notion credential", exception);
		}
	}

	@Override
	public String decrypt(long userId, String workspaceId, TokenType type, EncryptedToken encrypted) {
		byte[] key = keys.get(encrypted.keyVersion());
		if (key == null) {
			throw new TokenDecryptionException("Unknown Notion token key version");
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
				new GCMParameterSpec(TAG_BITS, encrypted.nonce()));
			cipher.updateAAD(aad(userId, workspaceId, type));
			return new String(cipher.doFinal(encrypted.ciphertext()), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException exception) {
			throw new TokenDecryptionException("Could not decrypt Notion credential", exception);
		}
	}

	private static byte[] aad(long userId, String workspaceId, TokenType type) {
		byte[] workspace = Objects.requireNonNull(workspaceId).getBytes(StandardCharsets.UTF_8);
		byte[] purpose = Objects.requireNonNull(type).name().getBytes(StandardCharsets.US_ASCII);
		return ByteBuffer.allocate(4 + Long.BYTES + Integer.BYTES + workspace.length + Integer.BYTES + purpose.length)
			.putInt(1).putLong(userId).putInt(workspace.length).put(workspace).putInt(purpose.length).put(purpose).array();
	}

	private static byte[] validateKey(String version, byte[] key) {
		if (version == null || version.isBlank() || key == null || key.length != 32) {
			throw new IllegalArgumentException("Each Notion token key must have a version and 32 bytes");
		}
		return key.clone();
	}
}
