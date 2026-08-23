package com.openmd.server.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
	@NotBlank @Email @Size(max = 320)
	@Schema(example = "learner@example.com") String email,
	@NotBlank @Size(max = 64)
	@Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, example = "<password>") String password
) {
}
