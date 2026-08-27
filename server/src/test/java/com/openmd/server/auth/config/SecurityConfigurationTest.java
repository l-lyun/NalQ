package com.openmd.server.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.CorsFilter;

class SecurityConfigurationTest {

	@Test
	void permitsViteDevelopmentOriginPreflightAndConfiguredCorsMethods() throws Exception {
		CorsFilter filter = new CorsFilter(SecurityConfiguration.buildCorsConfigurationSource(
			List.of("http://localhost:5173"),
			List.of("http://localhost:5173")
		));
		MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/sessions");
		request.addHeader("Origin", "http://localhost:5173");
		request.addHeader("Access-Control-Request-Method", "POST");
		request.addHeader("Access-Control-Request-Headers", "authorization,content-type");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
		assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("POST"));
		assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("authorization"));
	}

	@Test
	void learningMaterialPreflightAllowsPatchAndIdempotencyKey() throws Exception {
		CorsFilter filter = new CorsFilter(SecurityConfiguration.buildCorsConfigurationSource(
			List.of("http://localhost:5173"),
			List.of("http://localhost:5173")
		));
		MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/learning-materials");
		request.addHeader("Origin", "http://localhost:5173");
		request.addHeader("Access-Control-Request-Method", "POST");
		request.addHeader("Access-Control-Request-Headers", "authorization,content-type,idempotency-key");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("PATCH"));
		assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("idempotency-key"));
	}

	@Test
	void quizGradingPreflightAllowsPut() throws Exception {
		CorsFilter filter = new CorsFilter(SecurityConfiguration.buildCorsConfigurationSource(
			List.of("http://localhost:5173"),
			List.of("http://localhost:5173")
		));
		MockHttpServletRequest request = new MockHttpServletRequest(
			"OPTIONS", "/api/v1/quiz-attempts/attempt_1/grading-overrides/question_1"
		);
		request.addHeader("Origin", "http://localhost:5173");
		request.addHeader("Access-Control-Request-Method", "PUT");
		request.addHeader("Access-Control-Request-Headers", "authorization,content-type,idempotency-key");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertTrue(response.getHeader("Access-Control-Allow-Methods").contains("PUT"));
	}

	@Test
	void browserSessionCorsAllowsCredentialsAndTheCsrfHeaderOnlyForExactOrigin() throws Exception {
		CorsFilter filter = new CorsFilter(SecurityConfiguration.buildCorsConfigurationSource(
			List.of("http://localhost:5173"),
			List.of("http://localhost:5173")
		));
		MockHttpServletRequest request = new MockHttpServletRequest(
			"OPTIONS", "/api/v1/auth/web/sessions/refresh"
		);
		request.addHeader("Origin", "http://localhost:5173");
		request.addHeader("Access-Control-Request-Method", "POST");
		request.addHeader("Access-Control-Request-Headers", "content-type,x-openmd-csrf");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
		assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
		assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("x-openmd-csrf"));
	}

	@Test
	void nativeBodyCorsDoesNotAllowCredentials() throws Exception {
		CorsFilter filter = new CorsFilter(SecurityConfiguration.buildCorsConfigurationSource(
			List.of("http://localhost:5173"),
			List.of("http://localhost:5173")
		));
		MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/sessions/refresh");
		request.addHeader("Origin", "http://localhost:5173");
		request.addHeader("Access-Control-Request-Method", "POST");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertFalse(response.containsHeader("Access-Control-Allow-Credentials"));
	}

	@Test
	void rejectsWildcardBrowserOriginsBecauseCredentialedCorsRequiresExactOrigins() {
		assertThrows(IllegalArgumentException.class, () -> SecurityConfiguration.buildCorsConfigurationSource(
			List.of("http://localhost:5173"),
			List.of("*")
		));
	}

	@Test
	void rejectsWildcardGeneralOriginsBecauseBearerApiUsesAnExactAllowlist() {
		assertThrows(IllegalArgumentException.class, () -> SecurityConfiguration.buildCorsConfigurationSource(
			List.of("*"),
			List.of("http://localhost:5173")
		));
	}
}
