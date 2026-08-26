package com.openmd.server.notion.integration;

import com.openmd.server.notion.dto.model.NotionOAuthGrant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public final class HttpNotionOAuthClient implements NotionOAuthPort {

	private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE = new ParameterizedTypeReference<>() { };

	private final RestClient client;
	private final String basicAuthorization;
	private final String redirectUri;

	public HttpNotionOAuthClient(RestClient client, String clientId, String clientSecret, String redirectUri) {
		this.client = client;
		this.redirectUri = redirectUri;
		this.basicAuthorization = "Basic " + Base64.getEncoder().encodeToString(
			(clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)
		);
	}

	@Override
	public NotionOAuthGrant exchangeAuthorizationCode(String code) {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("grant_type", "authorization_code");
		body.put("code", code);
		body.put("redirect_uri", redirectUri);
		return token(body, false);
	}

	@Override
	public NotionOAuthGrant refresh(String refreshToken) {
		return token(Map.of("grant_type", "refresh_token", "refresh_token", refreshToken), true);
	}

	@Override
	public void revoke(String accessToken) {
		try {
			client.post()
				.uri("/v1/oauth/revoke")
				.header(HttpHeaders.AUTHORIZATION, basicAuthorization)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("token", accessToken))
				.retrieve()
				.toBodilessEntity();
		} catch (RestClientResponseException exception) {
			throw new NotionOAuthException(false);
		}
	}

	private NotionOAuthGrant token(Map<String, String> body, boolean refresh) {
		try {
			Map<String, Object> response = client.post()
				.uri("/v1/oauth/token")
				.header(HttpHeaders.AUTHORIZATION, basicAuthorization)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(MAP_TYPE);
			if (response == null) {
				throw new IllegalStateException("Notion OAuth response body is missing");
			}
			return new NotionOAuthGrant(
				required(response, "access_token"),
				required(response, "refresh_token"),
				required(response, "bot_id"),
				required(response, "workspace_id"),
				optional(response, "workspace_name"),
				optional(response, "workspace_icon")
			);
		} catch (RestClientResponseException exception) {
			boolean reauthenticationRequired = refresh && (exception.getStatusCode().value() == 400
				|| exception.getStatusCode().value() == 401);
			throw new NotionOAuthException(reauthenticationRequired);
		} catch (RuntimeException exception) {
			if (exception instanceof NotionOAuthException notionException) {
				throw notionException;
			}
			throw new NotionOAuthException(false);
		}
	}

	private String required(Map<String, Object> response, String key) {
		String value = optional(response, key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Notion OAuth response is incomplete");
		}
		return value;
	}

	private String optional(Map<String, Object> response, String key) {
		Object value = response.get(key);
		return value == null ? null : value.toString();
	}
}
