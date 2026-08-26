package com.openmd.server.notion.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.notion.dto.response.NotionAuthorization;
import com.openmd.server.notion.dto.response.NotionConnectionView;
import com.openmd.server.notion.service.NotionConnectionService;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notion")
@ConditionalOnProperty(name = "openmd.notion.enabled", havingValue = "true")
public class NotionConnectionController {

	private final NotionConnectionService service;

	public NotionConnectionController(NotionConnectionService service) {
		this.service = service;
	}

	@PostMapping("/authorizations")
	public ApiResponse<NotionAuthorization> startAuthorization(@AuthenticationPrincipal AccessPrincipal principal) {
		return ApiResponse.success(service.startAuthorization(principal.userId()));
	}

	@GetMapping("/oauth/callback")
	public ResponseEntity<Void> callback(
		@RequestParam(required = false) String code,
		@RequestParam String state,
		@RequestParam(required = false) String error
	) {
		URI redirect = error == null || error.isBlank()
			? service.completeAuthorization(code, state)
			: service.cancelAuthorization(state);
		return ResponseEntity.status(302).location(redirect).build();
	}

	@GetMapping("/connection")
	public ApiResponse<NotionConnectionView> connection(@AuthenticationPrincipal AccessPrincipal principal) {
		return ApiResponse.success(service.getConnection(principal.userId()));
	}

	@DeleteMapping("/connection")
	public ApiResponse<Void> disconnect(@AuthenticationPrincipal AccessPrincipal principal) {
		service.disconnect(principal.userId());
		return ApiResponse.successWithoutData();
	}
}
