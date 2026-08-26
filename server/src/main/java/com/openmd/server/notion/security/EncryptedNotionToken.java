package com.openmd.server.notion.security;

import java.util.Arrays;
import java.util.Objects;

public record EncryptedNotionToken(String keyVersion, byte[] ciphertext) {

	public EncryptedNotionToken {
		Objects.requireNonNull(keyVersion, "keyVersion must not be null");
		ciphertext = Arrays.copyOf(Objects.requireNonNull(ciphertext, "ciphertext must not be null"), ciphertext.length);
	}

	@Override
	public byte[] ciphertext() {
		return Arrays.copyOf(ciphertext, ciphertext.length);
	}
}
