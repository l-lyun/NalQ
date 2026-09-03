package com.openmd.server.integration.notion.client;

import java.util.List;

public record NotionMarkdown(String markdown, boolean truncated, List<String> unknownBlockIds) {
	public NotionMarkdown {
		unknownBlockIds = unknownBlockIds == null ? List.of() : List.copyOf(unknownBlockIds);
	}
}
