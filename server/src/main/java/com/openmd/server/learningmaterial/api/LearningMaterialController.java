package com.openmd.server.learningmaterial.api;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.learningmaterial.application.CreatedLearningMaterial;
import com.openmd.server.learningmaterial.application.LearningMaterialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-materials")
@ConditionalOnProperty(name = "openmd.learning-material.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Learning Materials", description = "인증 사용자의 학습자료 API")
public class LearningMaterialController {

	private final LearningMaterialService service;

	public LearningMaterialController(LearningMaterialService service) {
		this.service = service;
	}

	@PostMapping
	@Operation(
		operationId = "createLearningMaterial",
		summary = "학습자료를 저장한다",
		security = @SecurityRequirement(name = "bearerAuth"),
		responses = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "201", description = "학습자료 생성 성공", useReturnTypeSchema = true
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "400", description = "COMMON_001 입력 검증 실패 또는 COMMON_002 읽을 수 없는 JSON",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "401", description = "AUTH_005 Access Token 자격 없음 또는 만료",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "413", description = "MATERIAL_002 본문 20,000자 초과",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "500", description = "COMMON_999 예상하지 못한 서버 오류",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			)
		}
	)
	public ResponseEntity<ApiResponse<CreatedLearningMaterial>> create(
		@AuthenticationPrincipal AccessPrincipal principal,
		@Parameter(
			description = "같은 사용자 안에서 생성 재시도를 식별하는 공백 없는 출력 가능 ASCII 1~128자",
			required = true,
			example = "550e8400-e29b-41d4-a716-446655440000",
			schema = @Schema(type = "string", minLength = 1, maxLength = 128)
		)
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody CreateLearningMaterialRequest request
	) {
		CreatedLearningMaterial created = service.create(principal.userId(), idempotencyKey, request.toCommand());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
	}
}
