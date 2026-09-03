package com.openmd.server.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountWithdrawalRequest(
	@NotBlank @Size(max = 36) String withdrawalRequestId,
	@NotBlank @Size(max = 64) String currentPassword,
	@NotBlank @Size(max = 4) String confirmation
) {
}
