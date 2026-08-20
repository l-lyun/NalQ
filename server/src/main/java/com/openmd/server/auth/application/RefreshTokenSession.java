package com.openmd.server.auth.application;

import java.time.Instant;

public record RefreshTokenSession(
	long userId,
	String sessionId,
	Instant expiresAt
) {
}
