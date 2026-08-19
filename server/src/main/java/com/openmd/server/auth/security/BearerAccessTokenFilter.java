package com.openmd.server.auth.security;

import tools.jackson.databind.ObjectMapper;
import com.openmd.server.auth.domain.AuthErrorCode;
import com.openmd.server.global.api.ApiError;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.global.error.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class BearerAccessTokenFilter extends OncePerRequestFilter {

	private final AccessTokenService accessTokenService;
	private final ObjectMapper objectMapper;

	public BearerAccessTokenFilter(AccessTokenService accessTokenService, ObjectMapper objectMapper) {
		this.accessTokenService = accessTokenService;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/api/v1/auth/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			chain.doFilter(request, response);
			return;
		}
		try {
			AccessPrincipal principal = accessTokenService.verify(authorization.substring(7));
			SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, List.of())
			);
			chain.doFilter(request, response);
		} catch (BusinessException exception) {
			SecurityContextHolder.clearContext();
			writeUnauthorized(response);
		}
	}

	private void writeUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(ApiError.of(
			AuthErrorCode.INVALID_CREDENTIAL.code(), AuthErrorCode.INVALID_CREDENTIAL.message()
		)));
	}
}
