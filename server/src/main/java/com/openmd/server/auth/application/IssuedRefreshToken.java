package com.openmd.server.auth.application;

import java.time.Instant;

public record IssuedRefreshToken(String token, String sessionId, Instant expiresAt) {
}
