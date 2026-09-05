package com.openmd.server.integration.notion.client;

import java.util.List;

public record NotionPageSearch(List<NotionPageItem> items, String nextCursor) {
	public NotionPageSearch {
		items = List.copyOf(items);
	}
}
