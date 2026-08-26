package com.openmd.server.notion.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcmNotionTokenCipher implements NotionTokenCipher {

	private static final int NONCE_LENGTH = 12;
	private static final int TAG_LENGTH_BITS = 128;

	private final String activeKeyVersion;
	private final Map<String, SecretKeySpec> keys;
	private final SecureRandom random;

	public AesGcmNotionTokenCipher(String activeKeyVersion, Map<String, byte[]> keys) {
		this(activeKeyVersion, keys, new SecureRandom());
	}

	AesGcmNotionTokenCipher(String activeKeyVersion, Map<String, byte[]> keys, SecureRandom random) {
		this.activeKeyVersion = Objects.requireNonNull(activeKeyVersion, "activeKeyVersion must not be null");
		this.random = Objects.requireNonNull(random, "random must not be null");
		Map<String, SecretKeySpec> copied = new HashMap<>();
		for (Map.Entry<String, byte[]> entry : Objects.requireNonNull(keys, "keys must not be null").entrySet()) {
			if (entry.getValue().length != 32) {
				throw new IllegalArgumentException("Notion token encryption keys must be 256 bit");
			}
			copied.put(entry.getKey(), new SecretKeySpec(Arrays.copyOf(entry.getValue(), 32), "AES"));
		}
		if (!copied.containsKey(activeKeyVersion)) {
			throw new IllegalArgumentException("Active Notion token encryption key is missing");
		}
		this.keys = Map.copyOf(copied);
	}

	@Override
	public EncryptedNotionToken encrypt(String plaintext, NotionTokenContext context) {
		byte[] nonce = new byte[NONCE_LENGTH];
		random.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, keys.get(activeKeyVersion), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
			cipher.updateAAD(aad(context));
			byte[] encrypted = cipher.doFinal(Objects.requireNonNull(plaintext, "plaintext must not be null")
				.getBytes(StandardCharsets.UTF_8));
			return new EncryptedNotionToken(
				activeKeyVersion,
				ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array()
			);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Unable to encrypt Notion credential", exception);
		}
	}

	@Override
	public String decrypt(EncryptedNotionToken encrypted, NotionTokenContext context) {
		SecretKeySpec key = keys.get(encrypted.keyVersion());
		if (key == null || encrypted.ciphertext().length <= NONCE_LENGTH) {
			throw new IllegalStateException("Unable to decrypt Notion credential");
		}
		byte[] payload = encrypted.ciphertext();
		byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
		byte[] ciphertext = Arrays.copyOfRange(payload, NONCE_LENGTH, payload.length);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
			cipher.updateAAD(aad(context));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("Unable to decrypt Notion credential", exception);
		}
	}

	private byte[] aad(NotionTokenContext context) {
		return (context.userId() + "\n" + context.workspaceId() + "\n" + context.kind().name())
			.getBytes(StandardCharsets.UTF_8);
	}
}
