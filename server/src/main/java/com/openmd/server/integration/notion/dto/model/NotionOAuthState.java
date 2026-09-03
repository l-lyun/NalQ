package com.openmd.server.integration.notion.dto.model;

import java.time.Instant;

public record NotionOAuthState(
	long userId,
	String returnUri,
	String intent,
	Instant createdAt,
	String workspaceId,
	Long credentialRevision
) {}
