package com.openmd.server.notion.dto.response;

import com.openmd.server.notion.domain.NotionConnectionStatus;

public record NotionConnectionView(
	boolean connected,
	NotionConnectionStatus status,
	String workspaceId,
	String workspaceName,
	String workspaceIconUrl
) {
	public static NotionConnectionView disconnected() {
		return new NotionConnectionView(false, null, null, null, null);
	}
}
