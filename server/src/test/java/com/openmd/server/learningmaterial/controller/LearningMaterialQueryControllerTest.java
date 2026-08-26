package com.openmd.server.learningmaterial.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialDetail;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialPage;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialSummary;
import com.openmd.server.learningmaterial.service.LearningMaterialQueryService;
import com.openmd.server.learningmaterial.service.LearningMaterialService;
import com.openmd.server.learningmaterial.service.LearningMaterialUpdateService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class LearningMaterialQueryControllerTest {

	private final LearningMaterialService creation = mock(LearningMaterialService.class);
	private final LearningMaterialQueryService queries = mock(LearningMaterialQueryService.class);
	private final LearningMaterialUpdateService updates = mock(LearningMaterialUpdateService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new LearningMaterialController(creation, queries, updates))
			.setControllerAdvice(new com.openmd.server.global.error.GlobalExceptionHandler())
			.setCustomArgumentResolvers(accessPrincipalResolver())
			.build();
	}

	@Test
	void listsMaterialsUsingOneBasedDefaultsAndTheContractEnvelope() throws Exception {
		when(queries.list(7L, 1, 6, null)).thenReturn(new LearningMaterialPage(
			List.of(new LearningMaterialSummary(
				"31", "운영체제", SourceType.PASTE, ContentEditStatus.EDITABLE,
				Instant.parse("2026-08-26T01:00:00Z")
			)), 1, 6, 13, 3
		));

		mockMvc.perform(get("/api/v1/learning-materials"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.items[0].materialId").value("31"))
			.andExpect(jsonPath("$.data.items[0].content").doesNotExist())
			.andExpect(jsonPath("$.data.items[0].contentEditStatus").value("EDITABLE"))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.size").value(6))
			.andExpect(jsonPath("$.data.totalElements").value(13))
			.andExpect(jsonPath("$.data.totalPages").value(3));

		verify(queries).list(7L, 1, 6, null);
	}

	@Test
	void passesExplicitPaginationAndQueryToTheService() throws Exception {
		when(queries.list(7L, 2, 5, "운영")).thenReturn(new LearningMaterialPage(List.of(), 2, 5, 0, 0));

		mockMvc.perform(get("/api/v1/learning-materials")
				.param("page", "2").param("size", "5").param("query", "운영"))
			.andExpect(status().isOk());

		verify(queries).list(7L, 2, 5, "운영");
	}

	@Test
	void returnsDetailAndMapsMalformedMaterialIdToCommon001() throws Exception {
		when(queries.detail(7L, 31L)).thenReturn(new LearningMaterialDetail(
			"31", "운영체제", "본문😀", 3, SourceType.PASTE, ContentEditStatus.LOCKED_GENERATING,
			Instant.parse("2026-08-20T01:00:00Z"), Instant.parse("2026-08-26T01:00:00Z")
		));

		mockMvc.perform(get("/api/v1/learning-materials/31"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.materialId").value("31"))
			.andExpect(jsonPath("$.data.content").value("본문😀"))
			.andExpect(jsonPath("$.data.contentLength").value(3))
			.andExpect(jsonPath("$.data.contentEditStatus").value("LOCKED_GENERATING"));

		mockMvc.perform(get("/api/v1/learning-materials/not-a-number"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_001"));
	}

	@Test
	void mapsUnparseablePaginationToCommon001() throws Exception {
		mockMvc.perform(get("/api/v1/learning-materials").param("page", "first"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_001"));
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
