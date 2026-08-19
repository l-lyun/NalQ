package com.openmd.server.auth.application;

import java.time.Instant;

public record SessionTokens(
	String accessToken,
	Instant accessExpiresAt,
	String refreshToken,
	Instant refreshExpiresAt
) {
}
