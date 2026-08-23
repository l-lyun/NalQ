package com.openmd.server.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailVerificationConfirmRequest(
	@NotBlank @Email @Size(max = 320)
	@Schema(example = "learner@example.com") String email,
	@NotBlank @Size(max = 64)
	@Schema(description = "이메일로 받은 6자리 인증 코드", example = "<verification-code>") String code
) {
}
