package com.openmd.server.integration.notion.dto.response;

import com.openmd.server.integration.notion.client.NotionPageItem;
import java.util.List;

public record NotionPageList(List<NotionPageItem> items, String nextCursor) {
	public NotionPageList { items = List.copyOf(items); }
}
