package com.openmd.server.auth.api;

import com.openmd.server.auth.application.AuthService;
import com.openmd.server.auth.application.SessionTokens;
import com.openmd.server.global.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Authentication", description = "이메일 가입, 로그인과 Refresh Token 세션 관리")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/sign-ups")
	@Operation(
		operationId = "requestSignUp",
		summary = "이메일 회원가입을 요청한다",
		responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "202", description = "가입 요청 접수", useReturnTypeSchema = true
		)
	)
	public ResponseEntity<ApiResponse<VerificationRequiredResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
		authService.signUp(request.email(), request.password());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(ApiResponse.success(new VerificationRequiredResponse(true)));
	}

	@PostMapping("/email-verifications")
	@Operation(
		operationId = "resendEmailVerification",
		summary = "이메일 인증 코드를 다시 요청한다",
		responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "202", description = "재발송 요청 접수", useReturnTypeSchema = true
		)
	)
	public ResponseEntity<ApiResponse<VerificationRequiredResponse>> resend(@Valid @RequestBody EmailRequest request) {
		authService.resend(request.email());
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(ApiResponse.success(new VerificationRequiredResponse(true)));
	}

	@PostMapping("/email-verifications/confirm")
	@Operation(operationId = "confirmEmailVerification", summary = "이메일 인증 코드를 확인한다")
	public ApiResponse<EmailVerifiedResponse> confirm(@Valid @RequestBody EmailVerificationConfirmRequest request) {
		authService.confirm(request.email(), request.code());
		return ApiResponse.success(new EmailVerifiedResponse(true, "LOGIN"));
	}

	@PostMapping("/sessions")
	@Operation(
		operationId = "createSession",
		summary = "이메일과 비밀번호로 로그인한다",
		responses = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "200", description = "로그인 성공", useReturnTypeSchema = true
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "400", description = "COMMON_001 입력 검증 실패",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "401", description = "AUTH_001 로그인 실패",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			)
		}
	)
	public ApiResponse<SessionTokens> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.success(authService.login(request.email(), request.password()));
	}

	@PostMapping("/sessions/refresh")
	@Operation(
		operationId = "refreshSession",
		summary = "Access Token과 Refresh Token을 갱신한다",
		description = "성공하면 두 토큰이 모두 교체됩니다. 이전 Refresh Token을 다시 사용하면 세션이 폐기될 수 있습니다.",
		responses = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "200", description = "토큰 회전 성공", useReturnTypeSchema = true
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "400", description = "COMMON_001 입력 검증 실패",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "401", description = "AUTH_005 갱신 자격 없음, 만료 또는 재사용",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			)
		}
	)
	public ApiResponse<SessionTokens> refresh(@Valid @RequestBody RefreshTokenRequest request) {
		return ApiResponse.success(authService.refresh(request.refreshToken()));
	}

	@DeleteMapping("/sessions/current")
	@Operation(operationId = "deleteCurrentSession", summary = "현재 Refresh Token 세션을 로그아웃한다")
	public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
		authService.logout(request.refreshToken());
		return ApiResponse.successWithoutData();
	}
}
