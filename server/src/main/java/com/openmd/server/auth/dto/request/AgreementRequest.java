package com.openmd.server.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgreementRequest(
	@NotBlank @Size(max = 64) @Schema(example = "SERVICE_TERMS") String termsId,
	@NotBlank @Size(max = 64) @Schema(example = "2026-09-04") String version
) {
}
