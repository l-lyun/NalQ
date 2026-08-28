package com.openmd.server.learningmaterial.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.config.SecurityConfiguration;
import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.auth.security.AccessTokenService;
import com.openmd.server.global.error.GlobalExceptionHandler;
import com.openmd.server.learningmaterial.dto.response.CreatedLearningMaterial;
import com.openmd.server.learningmaterial.dto.response.LearningMaterialPage;
import com.openmd.server.learningmaterial.service.LearningMaterialService;
import com.openmd.server.learningmaterial.service.LearningMaterialQueryService;
import com.openmd.server.learningmaterial.service.LearningMaterialUpdateService;
import com.openmd.server.learningmaterial.domain.ContentEditStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
	classes = LearningMaterialSecurityTest.TestApplication.class,
	properties = {
		"springdoc.api-docs.enabled=false",
		"openmd.cors.allowed-origins=http://localhost:5173",
		"openmd.auth.browser.allowed-origins=http://localhost:5173",
		"spring.autoconfigure.exclude="
			+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
			+ "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
			+ "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
			+ "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
	}
)
@AutoConfigureMockMvc
class LearningMaterialSecurityTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean LearningMaterialService service;
	@MockitoBean LearningMaterialQueryService queryService;
	@MockitoBean LearningMaterialUpdateService updateService;
	@MockitoBean AccessTokenService accessTokenService;

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import({LearningMaterialController.class, SecurityConfiguration.class, GlobalExceptionHandler.class})
	static class TestApplication {
	}

	@Test
	void bearerPostDoesNotRequireCsrfTokenButStillRequiresAuthentication() throws Exception {
		when(accessTokenService.verify("valid-token")).thenReturn(new AccessPrincipal(7L, "session"));
		when(service.create(eq(7L), eq("request-1"), any())).thenReturn(new CreatedLearningMaterial(
			"31", "제목", 2, ContentEditStatus.EDITABLE,
			Instant.parse("2026-08-20T01:02:03Z")
		));

		mockMvc.perform(post("/api/v1/learning-materials")
				.header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
				.header("Idempotency-Key", "request-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"제목\",\"content\":\"본문\",\"sourceType\":\"PASTE\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.materialId").value("31"));

		mockMvc.perform(post("/api/v1/learning-materials")
				.header("Idempotency-Key", "request-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"제목\",\"content\":\"본문\",\"sourceType\":\"PASTE\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_005"));
	}

	@Test
	void learningMaterialQueriesRequireBearerAuthentication() throws Exception {
		when(accessTokenService.verify("valid-token")).thenReturn(new AccessPrincipal(7L, "session"));
		when(queryService.list(7L, 1, 6, null)).thenReturn(new LearningMaterialPage(List.of(), 1, 6, 0, 0));

		mockMvc.perform(get("/api/v1/learning-materials")
				.header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/learning-materials"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_005"));
	}
}
