package com.openmd.server.notion.config;

import com.openmd.server.notion.integration.HttpNotionOAuthClient;
import com.openmd.server.notion.integration.NotionOAuthPort;
import com.openmd.server.notion.repository.NotionConnectionRepository;
import com.openmd.server.notion.repository.NotionOAuthStateStore;
import com.openmd.server.notion.repository.redis.RedisNotionOAuthStateStore;
import com.openmd.server.notion.security.AesGcmNotionTokenCipher;
import com.openmd.server.notion.security.NotionTokenCipher;
import com.openmd.server.notion.service.NotionConnectionService;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "openmd.notion.enabled", havingValue = "true")
public class NotionConfiguration {

	@Bean
	NotionOAuthStateStore notionOAuthStateStore(StringRedisTemplate redis) {
		return new RedisNotionOAuthStateStore(redis);
	}

	@Bean
	NotionTokenCipher notionTokenCipher(
		@Value("${openmd.notion.token-encryption.active-key-version}") String activeVersion,
		@Value("${openmd.notion.token-encryption.active-key}") String activeKey,
		@Value("${openmd.notion.token-encryption.previous-key-version:}") String previousVersion,
		@Value("${openmd.notion.token-encryption.previous-key:}") String previousKey
	) {
		Map<String, byte[]> keyRing = new LinkedHashMap<>();
		keyRing.put(activeVersion, decodeKey(activeKey));
		if (!previousVersion.isBlank() || !previousKey.isBlank()) {
			if (previousVersion.isBlank() || previousKey.isBlank() || previousVersion.equals(activeVersion)) {
				throw new IllegalArgumentException("Previous Notion encryption key configuration is invalid");
			}
			keyRing.put(previousVersion, decodeKey(previousKey));
		}
		return new AesGcmNotionTokenCipher(activeVersion, keyRing);
	}

	@Bean
	NotionOAuthPort notionOAuthPort(
		@Value("${openmd.notion.api-base-url:https://api.notion.com}") String apiBaseUrl,
		@Value("${openmd.notion.client-id}") String clientId,
		@Value("${openmd.notion.client-secret}") String clientSecret,
		@Value("${openmd.notion.redirect-uri}") String redirectUri,
		@Value("${openmd.notion.connect-timeout:3s}") Duration connectTimeout,
		@Value("${openmd.notion.read-timeout:10s}") Duration readTimeout
	) {
		requireText(clientId, "Notion client id");
		requireText(clientSecret, "Notion client secret");
		return new HttpNotionOAuthClient(
			buildRestClient(apiBaseUrl, connectTimeout, readTimeout), clientId, clientSecret, redirectUri
		);
	}

	@Bean
	NotionConnectionService notionConnectionService(
		NotionConnectionRepository connections,
		NotionOAuthStateStore states,
		NotionOAuthPort oauth,
		NotionTokenCipher cipher,
		@Value("${openmd.notion.client-id}") String clientId,
		@Value("${openmd.notion.redirect-uri}") URI redirectUri,
		@Value("${openmd.notion.frontend-return-uri}") URI frontendReturnUri,
		@Value("${openmd.notion.oauth-state-ttl:10m}") Duration stateTtl
	) {
		if (stateTtl.isZero() || stateTtl.isNegative()) {
			throw new IllegalArgumentException("Notion OAuth state TTL must be positive");
		}
		validateAbsoluteHttpUri(redirectUri, "Notion redirect URI");
		validateAbsoluteHttpUri(frontendReturnUri, "Notion frontend return URI");
		return new NotionConnectionService(
			connections, states, oauth, cipher, clientId, redirectUri, frontendReturnUri, stateTtl
		);
	}

	private static byte[] decodeKey(String value) {
		try {
			return Base64.getDecoder().decode(value);
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("Notion token encryption key must be valid Base64", exception);
		}
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must be configured");
		}
	}

	private static void validateAbsoluteHttpUri(URI uri, String name) {
		if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
			throw new IllegalArgumentException(name + " must be an absolute HTTP(S) URI");
		}
	}

	static RestClient buildRestClient(String apiBaseUrl, Duration connectTimeout, Duration readTimeout) {
		if (connectTimeout.isZero() || connectTimeout.isNegative()) {
			throw new IllegalArgumentException("Notion connect timeout must be positive");
		}
		if (readTimeout.isZero() || readTimeout.isNegative()) {
			throw new IllegalArgumentException("Notion read timeout must be positive");
		}
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(connectTimeout)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder()
			.baseUrl(apiBaseUrl)
			.requestFactory(requestFactory)
			.build();
	}
}
