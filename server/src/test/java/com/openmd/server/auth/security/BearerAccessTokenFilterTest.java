package com.openmd.server.auth.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.global.error.BusinessException;
import java.util.Optional;

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
		UserRepository users = activeUsers(42L);
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, users, JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
		request.addHeader("Authorization", "Bearer " + token);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		AccessPrincipal principal = (AccessPrincipal) SecurityContextHolder.getContext()
			.getAuthentication().getPrincipal();
		assertEquals(42L, principal.userId());
		assertEquals("session-id", principal.sessionId());
	}

	@Test
	void rejectsAStillSignedAccessTokenAfterTheUserWasWithdrawn() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		String token = tokens.issue(42L, "session-id").token();
		UserRepository users = mock(UserRepository.class);
		User withdrawn = mock(User.class);
		when(withdrawn.getStatus()).thenReturn(UserStatus.WITHDRAWN);
		when(users.findById(42L)).thenReturn(Optional.of(withdrawn));
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, users, JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/learning-materials");
		request.addHeader("Authorization", "Bearer " + token);
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("AUTH_005"));
	}

	@Test
	void allowsTheWithdrawalEndpointToReplayTheSameRequestForAWithdrawnUser() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		String token = tokens.issue(42L, "session-id").token();
		UserRepository users = mock(UserRepository.class);
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, users, JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/users/me");
		request.addHeader("Authorization", "Bearer " + token);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		AccessPrincipal principal = (AccessPrincipal) SecurityContextHolder.getContext()
			.getAuthentication().getPrincipal();
		assertEquals(42L, principal.userId());
	}

	@Test
	void allowsWithdrawalReplayWhenTheApplicationUsesAContextPath() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		String token = tokens.issue(42L, "session-id").token();
		UserRepository users = mock(UserRepository.class);
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, users, JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/openmd/api/v1/users/me");
		request.setContextPath("/openmd");
		request.setServletPath("/api/v1/users/me");
		request.addHeader("Authorization", "Bearer " + token);

		filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

		assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
	}

	@Test
	void returnsTheStableCredentialErrorForAnInvalidBearerToken() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, mock(UserRepository.class), JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
		request.addHeader("Authorization", "Bearer forged-token");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(401, response.getStatus());
		assertTrue(response.getContentAsString().contains("AUTH_005"));
	}

	@Test
	void letsBusinessErrorsFromTheAuthenticatedRequestReachTheGlobalHandler() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		String token = tokens.issue(42L, "session-id").token();
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(
			tokens, activeUsers(42L), JsonMapper.builder().build()
		);
		MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/users/me");
		request.addHeader("Authorization", "Bearer " + token);

		BusinessException failure = org.junit.jupiter.api.Assertions.assertThrows(
			BusinessException.class,
			() -> filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> {
				throw new BusinessException(AuthErrorCode.WITHDRAWAL_PASSWORD_MISMATCH);
			})
		);

		assertEquals(AuthErrorCode.WITHDRAWAL_PASSWORD_MISMATCH, failure.getErrorCode());
	}

	@Test
	void ignoresInvalidBearerTokensOnPublicAuthenticationEndpoints() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, mock(UserRepository.class), JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/sessions");
		request.addHeader("Authorization", "Bearer expired-or-forged");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertEquals("", response.getContentAsString());
	}

	@Test
	void ignoresInvalidBearerTokensOnApiDocumentationEndpoints() throws Exception {
		AccessTokenService tokens = AccessTokenService.create(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));
		BearerAccessTokenFilter filter = new BearerAccessTokenFilter(tokens, mock(UserRepository.class), JsonMapper.builder().build());
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
		request.addHeader("Authorization", "Bearer expired-or-forged");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertEquals(200, response.getStatus());
		assertEquals("", response.getContentAsString());
	}

	private UserRepository activeUsers(long userId) {
		UserRepository users = mock(UserRepository.class);
		User active = mock(User.class);
		when(active.getStatus()).thenReturn(UserStatus.ACTIVE);
		when(active.getEmailVerifiedAt()).thenReturn(NOW.minusSeconds(30));
		when(users.findById(userId)).thenReturn(Optional.of(active));
		return users;
	}
}
