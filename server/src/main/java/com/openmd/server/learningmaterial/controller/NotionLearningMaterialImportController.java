package com.openmd.server.learningmaterial.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.integration.notion.dto.request.NotionImportRequest;
import com.openmd.server.integration.notion.dto.response.NotionImportedPage;
import com.openmd.server.integration.notion.service.NotionConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-material-imports")
@ConditionalOnProperty(name = "openmd.notion.enabled", havingValue = "true")
@Tag(name = "Learning Material Imports")
public class NotionLearningMaterialImportController {
	private final NotionConnectionService service;

	public NotionLearningMaterialImportController(NotionConnectionService service) {
		this.service = service;
	}

	@PostMapping("/notion")
	@Operation(operationId = "importNotionPage", security = @SecurityRequirement(name = "bearerAuth"))
	public ApiResponse<NotionImportedPage> importNotion(
		@AuthenticationPrincipal AccessPrincipal principal,
		@RequestBody NotionImportRequest request
	) {
		return ApiResponse.success(service.importPage(principal.userId(), request.pageId()));
	}
}
