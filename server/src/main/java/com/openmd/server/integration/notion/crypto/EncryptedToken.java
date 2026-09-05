package com.openmd.server.integration.notion.crypto;

public record EncryptedToken(byte[] ciphertext, byte[] nonce, String keyVersion) {
	public EncryptedToken {
		ciphertext = ciphertext.clone();
		nonce = nonce.clone();
	}

	@Override
	public byte[] ciphertext() {
		return ciphertext.clone();
	}

	@Override
	public byte[] nonce() {
		return nonce.clone();
	}
}
