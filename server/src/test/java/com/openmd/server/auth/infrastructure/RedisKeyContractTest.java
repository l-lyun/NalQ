package com.openmd.server.auth.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedisKeyContractTest {

	@Test
	void keepsRefreshSessionAndTombstonesInTheSameClusterHashSlot() {
		String session = RedisRefreshSessionStore.sessionKey("session123");
		String used = RedisRefreshSessionStore.usedKey("session123", "digest456");

		assertEquals("session123", hashTag(session));
		assertEquals(hashTag(session), hashTag(used));
		assertFalse(session.contains("digest456"));
	}

	@Test
	void emailVerificationKeyContainsOnlyTheInternalUserId() {
		assertEquals("auth:email-verification:user:{42}", RedisEmailVerificationStore.key(42L));
	}

	@Test
	void verificationAttemptsAndRefreshRotationAreEachOneAtomicLuaExecution() {
		String verificationScript = RedisEmailVerificationStore.VERIFY_SCRIPT.getScriptAsString();
		assertTrue(verificationScript.contains("HINCRBY"));
		assertTrue(verificationScript.contains("attempts >= 5"));
		assertTrue(verificationScript.contains("DEL"));

		String rotationScript = RedisRefreshSessionStore.ROTATE_SCRIPT.getScriptAsString();
		assertTrue(rotationScript.contains("EXISTS', KEYS[2]"));
		assertTrue(rotationScript.contains("currentTokenDigest"));
		assertTrue(rotationScript.contains("PEXPIREAT"));

		String createScript = RedisRefreshSessionStore.CREATE_SCRIPT.getScriptAsString();
		assertTrue(createScript.contains("HSET"));
		assertTrue(createScript.contains("PEXPIREAT"));

		String cancellationScript = RedisEmailVerificationStore.CANCEL_ISSUE_SCRIPT.getScriptAsString();
		assertTrue(cancellationScript.contains("codeDigest') == ARGV[1]"));
		assertTrue(cancellationScript.contains("DEL"));
	}

	private String hashTag(String key) {
		return key.substring(key.indexOf('{') + 1, key.indexOf('}'));
	}
}
