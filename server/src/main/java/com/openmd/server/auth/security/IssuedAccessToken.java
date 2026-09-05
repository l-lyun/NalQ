package com.openmd.server.auth.security;

import java.time.Instant;

public record IssuedAccessToken(String token, Instant expiresAt) {
}
