package com.openmd.server.auth.api;

import java.time.Instant;

public record BrowserSessionTokens(
	String accessToken,
	Instant accessExpiresAt,
	Instant refreshExpiresAt
) {
}
