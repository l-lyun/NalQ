package com.openmd.server.auth.dto.model;

import java.time.Instant;

public record IssuedRefreshToken(String token, String sessionId, Instant expiresAt) {
}
