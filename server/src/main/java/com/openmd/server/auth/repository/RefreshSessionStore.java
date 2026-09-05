package com.openmd.server.auth.repository;

import java.time.Instant;

public interface RefreshSessionStore {

	void create(String sessionId, long userId, String familyId, String digest, Instant expiresAt);

	InspectionResult inspect(String sessionId, String currentDigest);

	RotationResult rotate(String sessionId, String currentDigest, String newDigest);

	void revoke(String sessionId, String digest);

	void revokeAll(long userId);

	record InspectionResult(Status status, Long userId, Instant expiresAt) {
		public enum Status { VALID, INVALID, REUSED }
		public static InspectionResult valid(long userId, Instant expiresAt) {
			return new InspectionResult(Status.VALID, userId, expiresAt);
		}
		public static InspectionResult invalid() {
			return new InspectionResult(Status.INVALID, null, null);
		}
		public static InspectionResult reused() {
			return new InspectionResult(Status.REUSED, null, null);
		}
	}

	record RotationResult(Status status, Long userId, Instant expiresAt) {
		public enum Status { ROTATED, INVALID, REUSED }
		public static RotationResult rotated(long userId, Instant expiresAt) { return new RotationResult(Status.ROTATED, userId, expiresAt); }
		public static RotationResult invalid() { return new RotationResult(Status.INVALID, null, null); }
		public static RotationResult reused() { return new RotationResult(Status.REUSED, null, null); }
	}
}
