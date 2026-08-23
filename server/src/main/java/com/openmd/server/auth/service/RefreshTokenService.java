package com.openmd.server.auth.service;

import com.openmd.server.auth.dto.model.IssuedRefreshToken;
import com.openmd.server.auth.dto.model.RefreshTokenSession;
import com.openmd.server.auth.dto.model.RotatedRefreshToken;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.auth.repository.RefreshSessionStore;
import com.openmd.server.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

public final class RefreshTokenService {

	private final RefreshSessionStore store;
	private final Clock clock;
	private final Duration lifetime;
	private final SecureRandom random = new SecureRandom();

	public RefreshTokenService(RefreshSessionStore store, Clock clock, Duration lifetime) {
		this.store = store;
		this.clock = clock;
		this.lifetime = lifetime;
	}

	public IssuedRefreshToken issue(long userId) {
		String sessionId = randomValue(16);
		String secret = randomValue(32);
		Instant expiresAt = clock.instant().truncatedTo(ChronoUnit.MILLIS).plus(lifetime);
		store.create(sessionId, userId, sessionId, digest(secret), expiresAt);
		return new IssuedRefreshToken(sessionId + "." + secret, sessionId, expiresAt);
	}

	public RotatedRefreshToken rotate(String token) {
		Parts parts = parse(token);
		String newSecret = randomValue(32);
		RefreshSessionStore.RotationResult result = store.rotate(
			parts.sessionId(), digest(parts.secret()), digest(newSecret)
		);
		if (result.status() != RefreshSessionStore.RotationResult.Status.ROTATED) {
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
		}
		IssuedRefreshToken refreshToken = new IssuedRefreshToken(
			parts.sessionId() + "." + newSecret,
			parts.sessionId(),
			result.expiresAt()
		);
		return new RotatedRefreshToken(result.userId(), refreshToken);
	}

	public RefreshTokenSession inspect(String token) {
		Parts parts = parse(token);
		RefreshSessionStore.InspectionResult result = store.inspect(
			parts.sessionId(), digest(parts.secret())
		);
		if (result.status() != RefreshSessionStore.InspectionResult.Status.VALID) {
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
		}
		return new RefreshTokenSession(result.userId(), parts.sessionId(), result.expiresAt());
	}

	public void revoke(String token) {
		try {
			Parts parts = parse(token);
			store.revoke(parts.sessionId(), digest(parts.secret()));
		} catch (BusinessException ignored) {
			// Logout is intentionally idempotent for malformed or expired credentials.
		}
	}

	private Parts parse(String token) {
		if (token == null) {
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
		}
		String[] values = token.split("\\.", -1);
		if (values.length != 2
			|| !values[0].matches("[A-Za-z0-9_-]{22}")
			|| !values[1].matches("[A-Za-z0-9_-]{43}")) {
			throw new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
		}
		return new Parts(values[0], values[1]);
	}

	private String randomValue(int bytes) {
		byte[] value = new byte[bytes];
		random.nextBytes(value);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private String digest(String secret) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.US_ASCII));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to digest refresh token", exception);
		}
	}

	private record Parts(String sessionId, String secret) {
	}
}
