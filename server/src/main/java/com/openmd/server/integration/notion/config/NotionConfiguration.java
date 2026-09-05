package com.openmd.server.integration.notion.config;

import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.integration.notion.client.NotionClient;
import com.openmd.server.integration.notion.client.JdkNotionHttpTransport;
import com.openmd.server.integration.notion.client.RestNotionClient;
import com.openmd.server.integration.notion.crypto.AesGcmTokenCipher;
import com.openmd.server.integration.notion.crypto.TokenCipher;
import com.openmd.server.integration.notion.repository.NotionConnectionRepository;
import com.openmd.server.integration.notion.repository.NotionOAuthStateStore;
import com.openmd.server.integration.notion.repository.redis.RedisNotionOAuthStateStore;
import com.openmd.server.integration.notion.service.NotionConnectionService;
import com.openmd.server.integration.notion.service.NotionMarkdownProcessor;
import java.net.http.HttpClient;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
@ConditionalOnProperty(name = "openmd.notion.enabled", havingValue = "true")
public class NotionConfiguration {

	@Bean
	TokenCipher notionTokenCipher(
		@Value("${openmd.notion.token-keys}") String configuredKeys,
		@Value("${openmd.notion.write-key-version}") String writeVersion
	) {
		Map<String, byte[]> keys = new LinkedHashMap<>();
		for (String configured : configuredKeys.split(",")) {
			String[] pair = configured.strip().split(":", 2);
			if (pair.length != 2 || keys.putIfAbsent(pair[0], Base64.getDecoder().decode(pair[1])) != null) {
				throw new IllegalArgumentException("Invalid or duplicate Notion token key version");
			}
		}
		return new AesGcmTokenCipher(keys, writeVersion, new SecureRandom());
	}

	@Bean
	NotionOAuthStateStore notionOAuthStateStore(StringRedisTemplate redis, ObjectMapper mapper) {
		return new RedisNotionOAuthStateStore(redis, mapper);
	}

	@Bean
	NotionClient notionClient(
		@Value("${openmd.notion.api-base-url:https://api.notion.com}") String baseUrl,
		@Value("${openmd.notion.authorize-url:https://api.notion.com/v1/oauth/authorize}") String authorizeUrl,
		@Value("${openmd.notion.client-id}") String clientId,
		@Value("${openmd.notion.client-secret}") String clientSecret,
		@Value("${openmd.notion.callback-uri}") String callbackUri,
		@Value("${openmd.notion.api-version:2026-03-11}") String apiVersion,
		@Value("${openmd.notion.allowed-return-uris}") List<String> allowedReturnUris,
		@Value("${openmd.notion.failure-return-uri}") String failureReturnUri,
		ObjectMapper mapper,
		Clock clock
	) {
		validateClientSettings(
			clientId, clientSecret, baseUrl, authorizeUrl, callbackUri, allowedReturnUris, failureReturnUri
		);
		HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
		if (!"2026-03-11".equals(apiVersion)) {
			throw new IllegalArgumentException("Unsupported Notion API version");
		}
		return new RestNotionClient(
			new JdkNotionHttpTransport(http, mapper, baseUrl), mapper,
			clientId, clientSecret, callbackUri, authorizeUrl, clock
		);
	}

	static void validateClientSettings(
		String clientId,
		String clientSecret,
		String baseUrl,
		String authorizeUrl,
		String callbackUri,
		List<String> allowedReturnUris,
		String failureReturnUri
	) {
		if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
			throw new IllegalArgumentException("Notion OAuth client credentials are required");
		}
		validateHttpUri("Notion API base URL", baseUrl);
		validateHttpUri("Notion authorize URL", authorizeUrl);
		validateHttpUri("Notion callback URI", callbackUri);
		if (allowedReturnUris == null || allowedReturnUris.isEmpty()
			|| allowedReturnUris.stream().anyMatch(value -> !isValidHttpUri(value))) {
			throw new IllegalArgumentException("Notion frontend return URI allowlist is invalid");
		}
		if (!allowedReturnUris.contains(failureReturnUri)) {
			throw new IllegalArgumentException("Notion failure return URI must be in the exact allowlist");
		}
	}

	private static void validateHttpUri(String name, String value) {
		if (!isValidHttpUri(value)) throw new IllegalArgumentException(name + " is invalid");
	}

	private static boolean isValidHttpUri(String value) {
		if (value == null || value.isBlank()) return false;
		try {
			URI uri = URI.create(value);
			return uri.isAbsolute() && uri.getHost() != null && uri.getUserInfo() == null
				&& ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()));
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	@Bean
	NotionMarkdownProcessor notionMarkdownProcessor() { return new NotionMarkdownProcessor(); }

	@Bean
	NotionConnectionService notionConnectionService(
		NotionConnectionRepository connections,
		NotionOAuthStateStore states,
		NotionClient client,
		TokenCipher cipher,
		UserRepository users,
		NotionMarkdownProcessor markdown,
		Clock clock,
		@Value("${openmd.notion.allowed-return-uris}") List<String> allowedReturnUris,
		@Value("${openmd.notion.failure-return-uri}") String failureReturnUri
	) {
		return new NotionConnectionService(
			connections, states, client, cipher, users, markdown, clock, allowedReturnUris, failureReturnUri
		);
	}
}
