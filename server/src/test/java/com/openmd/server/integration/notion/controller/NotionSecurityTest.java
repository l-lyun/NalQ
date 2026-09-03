package com.openmd.server.integration.notion.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.config.SecurityConfiguration;
import com.openmd.server.auth.security.AccessTokenService;
import com.openmd.server.global.error.GlobalExceptionHandler;
import com.openmd.server.integration.notion.dto.response.NotionConnectionView;
import com.openmd.server.integration.notion.service.NotionConnectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
	classes = NotionSecurityTest.TestApplication.class,
	properties = {
		"springdoc.api-docs.enabled=false",
		"openmd.notion.enabled=true",
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
class NotionSecurityTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean AccessTokenService accessTokenService;
	@MockitoBean NotionConnectionService service;

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import({NotionIntegrationController.class, SecurityConfiguration.class, GlobalExceptionHandler.class})
	static class TestApplication {}

	@Test
	void onlyProviderCallbackIsPublicAndConnectionStatusStillRequiresBearer() throws Exception {
		when(service.connection(7L)).thenReturn(new NotionConnectionView("DISCONNECTED", null));
		when(service.completeAuthorization("missing", null, null))
			.thenReturn("https://app.openmd.example/learning/import/notion?outcome=failed&error=NOTION_CONNECTION_REQUIRED");

		mockMvc.perform(get("/api/v1/integrations/notion/callback").param("state", "missing"))
			.andExpect(status().isFound());

		mockMvc.perform(get("/api/v1/integrations/notion/connection"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_005"));
	}
}
