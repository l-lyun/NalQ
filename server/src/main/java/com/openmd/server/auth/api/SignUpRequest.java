package com.openmd.server.auth.api;

import com.openmd.server.auth.domain.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
	@NotBlank @Email @Size(max = 320)
	@Schema(example = "learner@example.com") String email,
	@NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = "8~64자이며 영문자와 숫자를 포함하고 공백이 없어야 합니다.")
	@Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, example = "<password>") String password
) {
}
