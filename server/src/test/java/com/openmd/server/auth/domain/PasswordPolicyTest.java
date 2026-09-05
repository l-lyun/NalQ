package com.openmd.server.auth.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

	@Test
	void acceptsEightToSixtyFourCharactersWithLettersAndDigits() {
		assertTrue(PasswordPolicy.isValid("password1"));
		assertTrue(PasswordPolicy.isValid("Abcdef12!@#$"));
		assertTrue(PasswordPolicy.isValid("a".repeat(63) + "1"));
	}

	@Test
	void rejectsMissingRequiredCharacterWhitespaceAndOutOfRangeLength() {
		assertFalse(PasswordPolicy.isValid("abcdefghi"));
		assertFalse(PasswordPolicy.isValid("12345678"));
		assertFalse(PasswordPolicy.isValid("abc 12345"));
		assertFalse(PasswordPolicy.isValid("abc1234"));
		assertFalse(PasswordPolicy.isValid("a".repeat(64) + "1"));
		assertFalse(PasswordPolicy.isValid(null));
	}
}
