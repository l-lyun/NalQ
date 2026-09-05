package com.openmd.server.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {

	@Test
	void issuesAnAccessTokenThatExpiresInExactlyFiveMinutes() {
		Instant now = Instant.parse("2026-08-19T00:00:00Z");
		String secret = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
		AccessTokenService service = AccessTokenService.create(secret, Clock.fixed(now, ZoneOffset.UTC));

		IssuedAccessToken issued = service.issue(42L, "session-id");
		AccessPrincipal principal = service.verify(issued.token());

		assertEquals(now.plus(Duration.ofMinutes(5)), issued.expiresAt());
		assertEquals(42L, principal.userId());
		assertEquals("session-id", principal.sessionId());
	}
}
