package com.openmd.server.learningmaterial.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.learningmaterial.dto.request.CreateLearningMaterialRequest;
import com.openmd.server.learningmaterial.dto.request.UpdateLearningMaterialRequest;
import com.openmd.server.learningmaterial.dto.response.CreatedLearningMaterial;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialDetail;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialPage;
import com.openmd.server.learningmaterial.service.LearningMaterialQueryService;
import com.openmd.server.learningmaterial.service.LearningMaterialService;
import com.openmd.server.learningmaterial.service.LearningMaterialUpdateService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
	private final LearningMaterialQueryService queries;
	private final LearningMaterialUpdateService updates;

	public LearningMaterialController(
		LearningMaterialService service,
		LearningMaterialQueryService queries,
		LearningMaterialUpdateService updates
	) {
		this.service = service;
		this.queries = queries;
		this.updates = updates;
	}

	@GetMapping
	@Operation(
		operationId = "listLearningMaterials",
		summary = "내 학습자료를 페이지로 조회한다",
		security = @SecurityRequirement(name = "bearerAuth"),
		responses = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "200", description = "학습자료 목록 조회 성공", useReturnTypeSchema = true
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "400", description = "COMMON_001 잘못된 페이지 조건",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "401", description = "AUTH_005 Access Token 자격 없음 또는 만료",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			)
		}
	)
	public ApiResponse<LearningMaterialPage> list(
		@AuthenticationPrincipal AccessPrincipal principal,
		@Parameter(description = "1부터 시작하는 페이지", example = "1", schema = @Schema(minimum = "1"))
		@RequestParam(defaultValue = "1") int page,
		@Parameter(description = "페이지당 항목 수", example = "6", schema = @Schema(minimum = "1", maximum = "20"))
		@RequestParam(defaultValue = "6") int size,
		@Parameter(description = "Unicode 앞뒤 공백을 제거해 적용하는 제목 부분 검색어")
		@RequestParam(required = false) String query
	) {
		return ApiResponse.success(queries.list(principal.userId(), page, size, query));
	}

	@GetMapping("/{materialId}")
	@Operation(
		operationId = "getLearningMaterial",
		summary = "내 학습자료 상세를 조회한다",
		security = @SecurityRequirement(name = "bearerAuth"),
		responses = {
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "200", description = "학습자료 상세 조회 성공", useReturnTypeSchema = true
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "400", description = "COMMON_001 잘못된 materialId",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "401", description = "AUTH_005 Access Token 자격 없음 또는 만료",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(
				responseCode = "404", description = "COMMON_003 자료 없음 또는 타 사용자 소유",
				content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))
			)
		}
	)
	public ApiResponse<LearningMaterialDetail> detail(
		@AuthenticationPrincipal AccessPrincipal principal,
		@Parameter(description = "학습자료 ID", example = "31")
		@PathVariable long materialId
	) {
		return ApiResponse.success(queries.detail(principal.userId(), materialId));
	}

	@PatchMapping("/{materialId}")
	public ApiResponse<LearningMaterialDetail> update(
		@AuthenticationPrincipal AccessPrincipal principal,
		@PathVariable long materialId,
		@RequestBody UpdateLearningMaterialRequest request
	) {
		return ApiResponse.success(updates.update(principal.userId(), materialId, request));
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
