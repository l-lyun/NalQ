package com.openmd.server.integration.notion.client;

import java.time.Instant;

public record NotionPageItem(String pageId, String title, Instant lastEditedAt) {}
