package com.openmd.server.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openmd.server.auth.domain.AuthErrorCode;
import com.openmd.server.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

	@Test
	void rotatesOpaqueRefreshTokensAndRejectsReuse() {
		Instant now = Instant.parse("2026-08-19T00:00:00Z");
		FakeRefreshSessionStore store = new FakeRefreshSessionStore();
		RefreshTokenService service = new RefreshTokenService(
			store,
			Clock.fixed(now, ZoneOffset.UTC),
			Duration.ofDays(30)
		);

		IssuedRefreshToken first = service.issue(7L);
		assertTrue(first.token().matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
		assertEquals(2, first.token().split("\\.", -1).length);
		assertEquals(now.plus(Duration.ofDays(30)), first.expiresAt());
		String storedDigest = store.sessions.get(first.sessionId()).digest();
		assertNotEquals(first.token(), storedDigest);
		assertNotEquals(first.token().split("\\.")[1], storedDigest);

		RotatedRefreshToken second = service.rotate(first.token());
		assertEquals(7L, second.userId());
		assertNotEquals(first.token(), second.refreshToken().token());

		BusinessException reused = assertThrows(BusinessException.class, () -> service.rotate(first.token()));
		assertEquals(AuthErrorCode.INVALID_CREDENTIAL, reused.getErrorCode());
	}

	@Test
	void logoutRequiresTheCurrentSecretButRemainsIdempotentForMalformedTokens() {
		Instant now = Instant.parse("2026-08-19T00:00:00Z");
		FakeRefreshSessionStore store = new FakeRefreshSessionStore();
		RefreshTokenService service = new RefreshTokenService(
			store,
			Clock.fixed(now, ZoneOffset.UTC),
			Duration.ofDays(30)
		);
		IssuedRefreshToken current = service.issue(7L);
		String forged = current.sessionId() + "." + "A".repeat(43);

		service.revoke(forged);
		RotatedRefreshToken rotated = service.rotate(current.token());
		service.revoke(rotated.refreshToken().token());
		service.revoke("malformed");

		assertThrows(BusinessException.class, () -> service.rotate(rotated.refreshToken().token()));
	}

	private static final class FakeRefreshSessionStore implements RefreshSessionStore {
		private final Map<String, Session> sessions = new HashMap<>();
		private final Set<String> used = new HashSet<>();

		@Override
		public void create(String sessionId, long userId, String familyId, String digest, Instant expiresAt) {
			sessions.put(sessionId, new Session(userId, digest));
		}

		@Override
		public RotationResult rotate(String sessionId, String currentDigest, String newDigest) {
			String usedKey = sessionId + ":" + currentDigest;
			if (used.contains(usedKey)) {
				sessions.remove(sessionId);
				return RotationResult.reused();
			}
			Session session = sessions.get(sessionId);
			if (session == null || !session.digest().equals(currentDigest)) {
				return RotationResult.invalid();
			}
			used.add(usedKey);
			sessions.put(sessionId, new Session(session.userId(), newDigest));
			return RotationResult.rotated(session.userId(), Instant.parse("2026-09-18T00:00:00Z"));
		}

		@Override
		public void revoke(String sessionId, String digest) {
			Session session = sessions.get(sessionId);
			if (session != null && session.digest().equals(digest)) {
				sessions.remove(sessionId);
			}
		}

		private record Session(long userId, String digest) {
		}
	}
}
