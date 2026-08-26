package com.openmd.server.notion.dto.model;

public record NotionOAuthGrant(
	String accessToken,
	String refreshToken,
	String botId,
	String workspaceId,
	String workspaceName,
	String workspaceIconUrl
) {
}
