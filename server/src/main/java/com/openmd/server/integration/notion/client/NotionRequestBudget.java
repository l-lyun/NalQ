package com.openmd.server.integration.notion.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public final class NotionRequestBudget {
	private static final Duration LIMIT = Duration.ofSeconds(20);
	private static final ThreadLocal<Instant> DEADLINE = new ThreadLocal<>();

	private NotionRequestBudget() {}

	public static <T> T within(Clock clock, Supplier<T> operation) {
		Instant previous = DEADLINE.get();
		if (previous != null) return operation.get();
		DEADLINE.set(clock.instant().plus(LIMIT));
		try {
			return operation.get();
		} finally {
			DEADLINE.remove();
		}
	}

	public static Duration remaining(Clock clock, Duration perRequestMaximum) {
		Instant deadline = DEADLINE.get();
		Duration remaining = deadline == null ? LIMIT : Duration.between(clock.instant(), deadline);
		if (remaining.isZero() || remaining.isNegative()) {
			throw new NotionClientException(NotionClientFailure.TEMPORARY);
		}
		return remaining.compareTo(perRequestMaximum) < 0 ? remaining : perRequestMaximum;
	}
}
