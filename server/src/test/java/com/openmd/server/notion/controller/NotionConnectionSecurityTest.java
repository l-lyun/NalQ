package com.openmd.server.notion.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.config.SecurityConfiguration;
import com.openmd.server.auth.security.AccessTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
	classes = NotionConnectionSecurityTest.TestApplication.class,
	properties = {
		"springdoc.api-docs.enabled=false",
		"spring.autoconfigure.exclude="
			+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
			+ "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
			+ "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
			+ "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
	}
)
@AutoConfigureMockMvc
class NotionConnectionSecurityTest {

	@Autowired MockMvc mvc;
	@MockitoBean AccessTokenService accessTokenService;

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import({SecurityConfiguration.class, TestEndpoints.class})
	static class TestApplication {
	}

	@RestController
	static class TestEndpoints {
		@GetMapping("/api/v1/notion/oauth/callback")
		String callback() {
			return "callback";
		}

		@GetMapping("/api/v1/notion/connection")
		String connection() {
			return "connection";
		}
	}

	@Test
	void permitsOnlyTheOauthCallbackWithoutAnOpenMdAccessToken() throws Exception {
		mvc.perform(get("/api/v1/notion/oauth/callback"))
			.andExpect(status().isOk())
			.andExpect(content().string("callback"));
		mvc.perform(get("/api/v1/notion/connection"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_005"));
	}
}
