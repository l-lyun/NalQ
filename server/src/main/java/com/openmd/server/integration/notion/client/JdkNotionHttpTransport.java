package com.openmd.server.integration.notion.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class JdkNotionHttpTransport implements NotionHttpTransport {
	private final HttpClient http;
	private final ObjectMapper mapper;
	private final String baseUrl;

	public JdkNotionHttpTransport(HttpClient http, ObjectMapper mapper, String baseUrl) {
		this.http = http;
		this.mapper = mapper;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	@Override
	public NotionHttpResponse exchange(String method, String path, Map<String, String> headers,
		Map<String, Object> body, Duration timeout) {
		try {
			HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(timeout);
			headers.forEach(request::header);
			if (body == null) {
				request.method(method, HttpRequest.BodyPublishers.noBody());
			} else {
				request.header("Content-Type", "application/json");
				request.method(method, HttpRequest.BodyPublishers.ofString(
					mapper.writeValueAsString(body), StandardCharsets.UTF_8
				));
			}
			HttpResponse<String> response = http.send(request.build(),
				HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			return new NotionHttpResponse(response.statusCode(), response.headers().map(), response.body());
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new NotionClientException(NotionClientFailure.TEMPORARY, exception);
		} catch (IOException | JacksonException | IllegalArgumentException exception) {
			throw new NotionClientException(NotionClientFailure.TEMPORARY, exception);
		}
	}
}
