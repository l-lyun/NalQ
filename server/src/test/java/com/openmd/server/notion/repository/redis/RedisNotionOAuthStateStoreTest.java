package com.openmd.server.notion.repository.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedisNotionOAuthStateStoreTest {

	@Test
	void keepsOnlyTheStateDigestInTheKeyAndConsumesWithOneAtomicScript() {
		String rawState = "raw-browser-state";
		String key = RedisNotionOAuthStateStore.key("digest-42");
		String script = RedisNotionOAuthStateStore.CONSUME_SCRIPT.getScriptAsString();

		assertEquals("notion:oauth-state:{digest-42}", key);
		assertFalse(key.contains(rawState));
		assertTrue(script.contains("GET"));
		assertTrue(script.contains("DEL"));
	}
}
