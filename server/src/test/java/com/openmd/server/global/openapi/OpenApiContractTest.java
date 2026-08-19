package com.openmd.server.global.openapi;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.api.AuthController;
import com.openmd.server.auth.api.UserController;
import com.openmd.server.auth.application.AuthService;
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

@SpringBootTest(
	classes = OpenApiContractTest.TestApplication.class,
	properties = {
		"springdoc.api-docs.enabled=true",
		"springdoc.swagger-ui.enabled=true",
		"springdoc.paths-to-match=/api/v1/**",
		"spring.autoconfigure.exclude="
			+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
			+ "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
			+ "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
			+ "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
	}
)
@AutoConfigureMockMvc
class OpenApiContractTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean AuthService authService;
	@MockitoBean AccessTokenService accessTokenService;

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import({AuthController.class, UserController.class, SecurityConfiguration.class, OpenApiConfiguration.class})
	static class TestApplication {
	}

	@Test
	void exposesTheOpenApiContractWhenDocumentationIsEnabled() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.openapi").value("3.1.0"))
			.andExpect(jsonPath("$.info.title").value("OpenMD API"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
			.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
			.andExpect(jsonPath("$.security[0].bearerAuth").isArray())
			.andExpect(jsonPath("$.paths['/api/v1/auth/sign-ups'].post.operationId").value("requestSignUp"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/sign-ups'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths['/api/v1/auth/sign-ups'].post.responses['202']").exists())
			.andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.operationId")
				.value("resendEmailVerification"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm'].post.operationId")
				.value("confirmEmailVerification"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/email-verifications/confirm'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions'].post.operationId").value("createSession"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions'].post.responses['200'].content"
				+ ".['application/json'].schema.$ref").value("#/components/schemas/ApiResponseSessionTokens"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/refresh'].post.operationId")
				.value("refreshSession"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/refresh'].post.security").isEmpty())
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/refresh'].post.requestBody.content"
				+ ".['application/json'].schema.$ref").value("#/components/schemas/RefreshTokenRequest"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/refresh'].post.parameters").doesNotExist())
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/refresh'].post.responses['400'].content"
				+ ".['application/json'].schema.$ref").value("#/components/schemas/ApiResponse"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/refresh'].post.responses['401'].content"
				+ ".['application/json'].schema.$ref").value("#/components/schemas/ApiResponse"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/current'].delete.operationId")
				.value("deleteCurrentSession"))
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/current'].delete.security").isEmpty())
			.andExpect(jsonPath("$.paths['/api/v1/auth/sessions/current'].delete.requestBody.content"
				+ ".['application/json'].schema.$ref").value("#/components/schemas/RefreshTokenRequest"))
			.andExpect(jsonPath("$.paths['/api/v1/users/me'].get.operationId").value("getCurrentUser"))
			.andExpect(jsonPath("$.paths['/api/v1/users/me'].get.security").doesNotExist())
			.andExpect(jsonPath("$.components.schemas.RefreshTokenRequest.properties.refreshToken.maxLength")
				.value(128))
			.andExpect(jsonPath("$.components.schemas.SessionTokens.properties.accessToken.type").value("string"))
			.andExpect(jsonPath("$.components.schemas.SessionTokens.properties.refreshToken.type").value("string"))
			.andExpect(content().string(not(containsString("eyJ"))));
	}

	@Test
	void exposesSwaggerUiWhenDocumentationIsEnabled() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/swagger-ui/index.html"));
	}
}
