package com.openmd.server.auth.application;

import java.time.Instant;

public interface RefreshSessionStore {

	void create(String sessionId, long userId, String familyId, String digest, Instant expiresAt);

	RotationResult rotate(String sessionId, String currentDigest, String newDigest);

	void revoke(String sessionId, String digest);

	record RotationResult(Status status, Long userId, Instant expiresAt) {
		public enum Status { ROTATED, INVALID, REUSED }
		public static RotationResult rotated(long userId, Instant expiresAt) { return new RotationResult(Status.ROTATED, userId, expiresAt); }
		public static RotationResult invalid() { return new RotationResult(Status.INVALID, null, null); }
		public static RotationResult reused() { return new RotationResult(Status.REUSED, null, null); }
	}
}
