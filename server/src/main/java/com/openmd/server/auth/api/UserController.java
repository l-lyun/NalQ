package com.openmd.server.auth.api;

import com.openmd.server.auth.application.AuthService;
import com.openmd.server.auth.application.CurrentUser;
import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@ConditionalOnProperty(name = "openmd.auth.enabled", havingValue = "true", matchIfMissing = true)
public class UserController {

	private final AuthService authService;

	public UserController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping("/me")
	public ApiResponse<CurrentUser> me(@AuthenticationPrincipal AccessPrincipal principal) {
		return ApiResponse.success(authService.currentUser(principal.userId()));
	}
}
