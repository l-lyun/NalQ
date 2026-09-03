package com.openmd.server.integration.notion.client;

import java.util.List;
import java.util.Map;

public record NotionHttpResponse(int status, Map<String, List<String>> headers, String body) {
	public String firstHeader(String name) {
		return headers.entrySet().stream()
			.filter(entry -> entry.getKey().equalsIgnoreCase(name))
			.flatMap(entry -> entry.getValue().stream())
			.findFirst().orElse(null);
	}
}
