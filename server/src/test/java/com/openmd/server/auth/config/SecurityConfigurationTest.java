package com.openmd.server.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
