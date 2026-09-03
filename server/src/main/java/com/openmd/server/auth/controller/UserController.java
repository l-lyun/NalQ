package com.openmd.server.auth.controller;

import com.openmd.server.auth.service.AuthService;
import com.openmd.server.auth.service.AccountWithdrawalService;
import com.openmd.server.auth.controller.support.BrowserRefreshCookie;
import com.openmd.server.auth.dto.request.AccountWithdrawalRequest;
import com.openmd.server.auth.dto.response.AccountWithdrawalResult;
import com.openmd.server.auth.dto.response.CurrentUser;
import com.openmd.server.auth.dto.request.UpdateNicknameRequest;
import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/v1/users")
@ConditionalOnProperty(name = "openmd.auth.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Users", description = "인증된 사용자 API")
public class UserController {

	private final AuthService authService;
	private final AccountWithdrawalService withdrawalService;
	private final BrowserRefreshCookie refreshCookie;

	public UserController(
		AuthService authService,
		AccountWithdrawalService withdrawalService,
		BrowserRefreshCookie refreshCookie
	) {
		this.authService = authService;
		this.withdrawalService = withdrawalService;
		this.refreshCookie = refreshCookie;
	}

	@GetMapping("/me")
	@Operation(
		operationId = "getCurrentUser",
		summary = "현재 로그인한 사용자를 조회한다",
		responses = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "200", description = "현재 사용자 조회 성공", useReturnTypeSchema = true
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "401", description = "AUTH_005 Access Token 자격 없음 또는 만료",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			)
		}
	)
	public ApiResponse<CurrentUser> me(@AuthenticationPrincipal AccessPrincipal principal) {
		return ApiResponse.success(authService.currentUser(principal.userId()));
	}

	@PatchMapping("/me")
	@Operation(
		operationId = "updateCurrentUserNickname",
		summary = "현재 로그인한 사용자의 닉네임을 변경한다",
		responses = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "200", description = "닉네임 변경 성공", useReturnTypeSchema = true
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "409", description = "AUTH_010 이미 사용 중인 닉네임",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			)
		}
	)
	public ApiResponse<CurrentUser> updateNickname(
		@AuthenticationPrincipal AccessPrincipal principal,
		@Valid @RequestBody UpdateNicknameRequest request
	) {
		return ApiResponse.success(authService.updateNickname(principal.userId(), request.nickname()));
	}

	@DeleteMapping("/me")
	@Operation(
		operationId = "withdrawCurrentUser",
		summary = "현재 로그인한 사용자 계정을 탈퇴 처리한다",
		responses = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "200", description = "회원 탈퇴 성공", useReturnTypeSchema = true
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "401", description = "AUTH_012 현재 비밀번호 불일치",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "503", description = "AUTH_013 탈퇴 DB 확정 실패",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			)
		}
	)
	public ResponseEntity<ApiResponse<AccountWithdrawalResult>> withdraw(
		@AuthenticationPrincipal AccessPrincipal principal,
		@Valid @RequestBody AccountWithdrawalRequest request
	) {
		AccountWithdrawalResult result = withdrawalService.withdraw(
			principal.userId(),
			request.withdrawalRequestId(),
			request.currentPassword(),
			request.confirmation()
		);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie.expire().toString())
			.body(ApiResponse.success(result));
	}
}
