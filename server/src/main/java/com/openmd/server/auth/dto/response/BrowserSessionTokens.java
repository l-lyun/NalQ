package com.openmd.server.auth.dto.response;

import java.time.Instant;

public record BrowserSessionTokens(
	String accessToken,
	Instant accessExpiresAt,
	Instant refreshExpiresAt
) {
}
