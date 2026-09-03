package com.openmd.server.integration.notion.crypto;

public class TokenDecryptionException extends RuntimeException {
	public TokenDecryptionException(String message, Throwable cause) {
		super(message, cause);
	}

	public TokenDecryptionException(String message) {
		super(message);
	}
}
