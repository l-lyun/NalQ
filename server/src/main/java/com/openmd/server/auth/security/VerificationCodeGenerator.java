package com.openmd.server.auth.security;

import java.security.SecureRandom;

public final class VerificationCodeGenerator {

	public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
	private final SecureRandom secureRandom;

	public VerificationCodeGenerator(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
	}

	public String generate() {
		StringBuilder code = new StringBuilder(6);
		for (int i = 0; i < 6; i++) {
			code.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}
}
