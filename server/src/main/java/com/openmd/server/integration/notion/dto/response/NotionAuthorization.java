package com.openmd.server.integration.notion.dto.response;

import java.time.Instant;

public record NotionAuthorization(String authorizationUrl, Instant expiresAt) {}
