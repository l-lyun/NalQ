package com.openmd.server.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class EmailVerificationPrimitivesTest {

	@Test
	void generatesSixCharactersOnlyFromTheApprovedAlphabet() {
		VerificationCodeGenerator generator = new VerificationCodeGenerator(new SecureRandom());

		for (int i = 0; i < 100; i++) {
			String code = generator.generate();
			assertEquals(6, code.length());
			assertTrue(code.matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}"));
		}
	}

	@Test
	void createsAKeyedUserAndPurposeSeparatedDigest() {
		VerificationCodeDigest digest = new VerificationCodeDigest("0123456789abcdef0123456789abcdef".getBytes());

		assertEquals(digest.create(1L, "A7K9M2"), digest.create(1L, "A7K9M2"));
		assertNotEquals(digest.create(1L, "A7K9M2"), digest.create(2L, "A7K9M2"));
		assertNotEquals("A7K9M2", digest.create(1L, "A7K9M2"));
	}
}
