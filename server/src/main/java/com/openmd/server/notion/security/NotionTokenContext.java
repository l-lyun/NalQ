package com.openmd.server.notion.security;

import java.util.Objects;

public record NotionTokenContext(long userId, String workspaceId, NotionTokenKind kind) {

	public NotionTokenContext {
		Objects.requireNonNull(workspaceId, "workspaceId must not be null");
		Objects.requireNonNull(kind, "kind must not be null");
	}
}
