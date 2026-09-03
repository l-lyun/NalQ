package com.openmd.server.integration.notion.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.integration.notion.dto.request.NotionAuthorizationRequest;
import com.openmd.server.integration.notion.dto.response.NotionAuthorization;
import com.openmd.server.integration.notion.dto.response.NotionConnectionView;
import com.openmd.server.integration.notion.dto.response.NotionDisconnected;
import com.openmd.server.integration.notion.dto.response.NotionPageList;
import com.openmd.server.integration.notion.service.NotionConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/notion")
@ConditionalOnProperty(name = "openmd.notion.enabled", havingValue = "true")
@Tag(name = "Notion Integration")
public class NotionIntegrationController {
	private final NotionConnectionService service;

	public NotionIntegrationController(NotionConnectionService service) {
		this.service = service;
	}

	@GetMapping("/connection")
	@Operation(operationId = "getNotionConnection", security = @SecurityRequirement(name = "bearerAuth"))
	public ApiResponse<NotionConnectionView> connection(@AuthenticationPrincipal AccessPrincipal principal) {
		return ApiResponse.success(service.connection(principal.userId()));
	}

	@PostMapping("/authorizations")
	@Operation(
		operationId = "startNotionAuthorization",
		security = @SecurityRequirement(name = "bearerAuth"),
		responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "201", description = "Notion OAuth 승인 시작", useReturnTypeSchema = true
		)
	)
	public ResponseEntity<ApiResponse<NotionAuthorization>> authorize(
		@AuthenticationPrincipal AccessPrincipal principal,
		@RequestBody NotionAuthorizationRequest request
	) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(service.startAuthorization(principal.userId(), request.returnUri())));
	}

	@GetMapping("/callback")
	@Operation(
		operationId = "completeNotionAuthorization",
		security = {},
		responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
			responseCode = "302", description = "등록된 프론트 복귀 URI로 이동"
		)
	)
	public ResponseEntity<Void> callback(
		@RequestParam(required = false) String state,
		@RequestParam(required = false) String code,
		@RequestParam(required = false) String error
	) {
		return ResponseEntity.status(HttpStatus.FOUND)
			.location(URI.create(service.completeAuthorization(state, code, error)))
			.build();
	}

	@GetMapping("/pages")
	@Operation(operationId = "listNotionPages", security = @SecurityRequirement(name = "bearerAuth"))
	public ApiResponse<NotionPageList> pages(
		@AuthenticationPrincipal AccessPrincipal principal,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) String query
	) {
		return ApiResponse.success(service.pages(principal.userId(), cursor, query));
	}

	@DeleteMapping("/connection")
	@Operation(operationId = "disconnectNotion", security = @SecurityRequirement(name = "bearerAuth"))
	public ApiResponse<NotionDisconnected> disconnect(@AuthenticationPrincipal AccessPrincipal principal) {
		return ApiResponse.success(service.disconnect(principal.userId()));
	}
}
