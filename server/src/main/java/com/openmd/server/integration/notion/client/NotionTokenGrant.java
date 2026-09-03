package com.openmd.server.integration.notion.client;

public record NotionTokenGrant(
	String accessToken,
	String refreshToken,
	String workspaceId,
	String workspaceName
) {}
