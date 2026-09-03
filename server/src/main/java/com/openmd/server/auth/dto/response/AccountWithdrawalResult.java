package com.openmd.server.auth.dto.response;

import com.openmd.server.auth.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record AccountWithdrawalResult(
	UUID withdrawalRequestId,
	UserStatus status,
	Instant withdrawnAt,
	Instant dataDisposalDeadline
) {
}
