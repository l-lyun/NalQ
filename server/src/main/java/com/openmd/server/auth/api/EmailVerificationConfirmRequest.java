package com.openmd.server.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationConfirmRequest(
	@NotBlank @Email @Size(max = 320) String email,
	@NotBlank @Size(max = 64) String code
) {
}
