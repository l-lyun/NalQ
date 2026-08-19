package com.openmd.server.auth.api;

import com.openmd.server.auth.application.AuthService;
import com.openmd.server.auth.application.SessionTokens;
import com.openmd.server.global.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "openmd.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/sign-ups")
	public ResponseEntity<ApiResponse<VerificationRequiredResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
		authService.signUp(request.email(), request.password());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(ApiResponse.success(new VerificationRequiredResponse(true)));
	}

	@PostMapping("/email-verifications")
	public ResponseEntity<ApiResponse<VerificationRequiredResponse>> resend(@Valid @RequestBody EmailRequest request) {
		authService.resend(request.email());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(ApiResponse.success(new VerificationRequiredResponse(true)));
	}

	@PostMapping("/email-verifications/confirm")
	public ApiResponse<EmailVerifiedResponse> confirm(@Valid @RequestBody EmailVerificationConfirmRequest request) {
		authService.confirm(request.email(), request.code());
		return ApiResponse.success(new EmailVerifiedResponse(true, "LOGIN"));
	}

	@PostMapping("/sessions")
	public ApiResponse<SessionTokens> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(authService.login(request.email(), request.password()));
	}

	@PostMapping("/sessions/refresh")
	public ApiResponse<SessionTokens> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return ApiResponse.success(authService.refresh(request.refreshToken()));
	}

	@DeleteMapping("/sessions/current")
	public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
		return ApiResponse.successWithoutData();
	}
}
