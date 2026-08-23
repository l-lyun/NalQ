package com.openmd.server.learningmaterial.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.dto.command.CreateLearningMaterialCommand;
import com.openmd.server.learningmaterial.dto.response.CreatedLearningMaterial;
import com.openmd.server.learningmaterial.service.LearningMaterialService;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class LearningMaterialControllerTest {

	private final LearningMaterialService service = mock(LearningMaterialService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new LearningMaterialController(service))
			.setControllerAdvice(new com.openmd.server.global.error.GlobalExceptionHandler())
			.setCustomArgumentResolvers(accessPrincipalResolver())
			.build();
	}

	@Test
	void createsOwnedMaterialAndReturnsTheContractEnvelope() throws Exception {
		when(service.create(7L, "request-1", new CreateLearningMaterialCommand(" 제목 ", "본문😀", "PASTE")))
			.thenReturn(new CreatedLearningMaterial(
				"31", "제목", 3, ContentEditStatus.EDITABLE,
				Instant.parse("2026-08-20T01:02:03Z")
			));

		mockMvc.perform(post("/api/v1/learning-materials")
				.header("Idempotency-Key", "request-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"userId":999,"title":" 제목 ","content":"본문😀","sourceType":"PASTE"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.materialId").value("31"))
			.andExpect(jsonPath("$.data.title").value("제목"))
			.andExpect(jsonPath("$.data.contentLength").value(3))
			.andExpect(jsonPath("$.data.contentEditStatus").value("EDITABLE"))
			.andExpect(jsonPath("$.data.sourceType").doesNotExist())
			.andExpect(jsonPath("$.data.createdAt").value("2026-08-20T01:02:03Z"));

		verify(service).create(7L, "request-1", new CreateLearningMaterialCommand(" 제목 ", "본문😀", "PASTE"));
	}

	@Test
	void mapsUnknownSourceToCommon001AndMalformedJsonToCommon002() throws Exception {
		CreateLearningMaterialCommand invalid = new CreateLearningMaterialCommand("제목", "본문", "UNKNOWN");
		when(service.create(7L, "request-2", invalid)).thenThrow(new BusinessException(
			CommonErrorCode.INVALID_INPUT,
			List.of(new com.openmd.server.global.api.FieldError("sourceType", "PASTE 또는 NOTION이어야 합니다."))
		));

		mockMvc.perform(post("/api/v1/learning-materials")
				.header("Idempotency-Key", "request-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"제목\",\"content\":\"본문\",\"sourceType\":\"UNKNOWN\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_001"))
			.andExpect(jsonPath("$.error.fields[0].field").value("sourceType"));

		mockMvc.perform(post("/api/v1/learning-materials")
				.header("Idempotency-Key", "request-3")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_002"));
	}

	private HandlerMethodArgumentResolver accessPrincipalResolver() {
		return new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType() == AccessPrincipal.class;
			}

			@Override
			public Object resolveArgument(
				MethodParameter parameter,
				ModelAndViewContainer container,
				NativeWebRequest request,
				org.springframework.web.bind.support.WebDataBinderFactory binderFactory
			) {
				return new AccessPrincipal(7L, "session");
			}
		};
	}
}
