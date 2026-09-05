package com.openmd.server.integration.notion.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotionConfigurationValidationTest {
	@Test
	void rejectsBlankSecretsAndUrisBeforeCreatingAnyProviderClient() {
		assertThrows(IllegalArgumentException.class, () -> NotionConfiguration.validateClientSettings(
			"", "secret", "https://api.notion.com", "https://api.notion.com/v1/oauth/authorize",
			"https://server.openmd.test/callback", List.of("https://app.openmd.test/import"),
			"https://app.openmd.test/import"
		));
		assertThrows(IllegalArgumentException.class, () -> NotionConfiguration.validateClientSettings(
			"client", "secret", "not-a-uri", "https://api.notion.com/v1/oauth/authorize",
			"https://server.openmd.test/callback", List.of("https://app.openmd.test/import"),
			"https://app.openmd.test/import"
		));
	}

	@Test
	void requiresTheFixedFailureUriToBelongToTheExactFrontendAllowlist() {
		assertThrows(IllegalArgumentException.class, () -> NotionConfiguration.validateClientSettings(
			"client", "secret", "https://api.notion.com", "https://api.notion.com/v1/oauth/authorize",
			"https://server.openmd.test/callback", List.of("https://app.openmd.test/import"),
			"https://attacker.test/import"
		));
	}
}
