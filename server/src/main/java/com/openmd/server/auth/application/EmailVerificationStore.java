package com.openmd.server.auth.application;

import java.time.Duration;
import java.time.Instant;

public interface EmailVerificationStore {

	IssueResult issue(long userId, String digest, Instant now, Duration ttl, Duration resendCooldown, boolean enforceCooldown);

	VerificationResult verify(long userId, String digest);

	boolean cancelIssue(long userId, String digest);

	void consume(long userId);

	record IssueResult(boolean issued, long retryAfterSeconds) {
		public static IssueResult success() { return new IssueResult(true, 0); }
		public static IssueResult limited(long retryAfterSeconds) { return new IssueResult(false, retryAfterSeconds); }
	}

	enum VerificationResult {
		MATCHED,
		MISMATCHED,
		EXPIRED
	}
}
