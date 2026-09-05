package com.openmd.server.auth.dto.response;

import java.time.Instant;

public record SessionTokens(
	String accessToken,
	Instant accessExpiresAt,
	String refreshToken,
	Instant refreshExpiresAt
) {
}
