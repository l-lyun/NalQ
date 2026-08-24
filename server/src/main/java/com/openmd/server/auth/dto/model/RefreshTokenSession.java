package com.openmd.server.auth.dto.model;

import java.time.Instant;

public record RefreshTokenSession(
	long userId,
	String sessionId,
	Instant expiresAt
) {
}
