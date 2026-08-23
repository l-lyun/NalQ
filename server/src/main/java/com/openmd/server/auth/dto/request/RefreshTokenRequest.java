package com.openmd.server.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Refresh Token 회전 또는 현재 세션 폐기 요청")
public record RefreshTokenRequest(
	@NotBlank
	@Size(max = 128)
	@Schema(description = "가장 최근에 발급된 opaque Refresh Token", example = "<refresh-token>")
	String refreshToken
) {
}
