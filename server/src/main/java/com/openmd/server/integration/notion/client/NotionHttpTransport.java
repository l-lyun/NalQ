package com.openmd.server.integration.notion.client;

import java.time.Duration;
import java.util.Map;

public interface NotionHttpTransport {
	NotionHttpResponse exchange(
		String method,
		String path,
		Map<String, String> headers,
		Map<String, Object> body,
		Duration timeout
	);
}
