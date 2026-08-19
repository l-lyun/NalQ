package com.openmd.server.auth.domain;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordHashingTest {

	@Test
	void storesPasswordsAsSaltedArgon2idHashes() {
		PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

		String first = encoder.encode("password1");
		String second = encoder.encode("password1");

		assertTrue(first.startsWith("$argon2id$"));
		assertNotEquals("password1", first);
		assertNotEquals(first, second);
		assertTrue(encoder.matches("password1", first));
	}
}
