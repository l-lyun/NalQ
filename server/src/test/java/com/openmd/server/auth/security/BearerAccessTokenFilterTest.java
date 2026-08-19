package com.openmd.server.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class BearerAccessTokenFilterTest {

	private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
	private static final String SECRET = Base64.getEncoder()
		.encodeToString("0123456789abcdef0123456789abcdef".getBytes());

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void authenticatesOnlyFromTheSignedBearerTokenPrincipal() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		String token = tokens.issue(42L, "session-id").token();
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
		request.addHeader("Authorization", "Bearer " + token);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		AccessPrincipal principal = (AccessPrincipal) SecurityContextHolder.getContext()
			.getAuthentication().getPrincipal();
		assertEquals(42L, principal.userId());
		assertEquals("session-id", principal.sessionId());
	}

	@Test
	void returnsTheStableCredentialErrorForAnInvalidBearerToken() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
		request.addHeader("Authorization", "Bearer forged-token");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("AUTH_005"));
	}

	@Test
	void ignoresInvalidBearerTokensOnPublicAuthenticationEndpoints() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/sessions");
		request.addHeader("Authorization", "Bearer expired-or-forged");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertEquals("", response.getContentAsString());
	}
}
