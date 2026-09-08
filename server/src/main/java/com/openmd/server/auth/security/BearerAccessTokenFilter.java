package com.openmd.server.auth.security;

import tools.jackson.databind.ObjectMapper;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.repository.UserRepository;
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
	private final UserRepository userRepository;
	private final ObjectMapper objectMapper;

	public BearerAccessTokenFilter(
		AccessTokenService accessTokenService,
		UserRepository userRepository,
		ObjectMapper objectMapper
	) {
		this.accessTokenService = accessTokenService;
		this.userRepository = userRepository;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String requestUri = request.getRequestURI();
		return requestUri.startsWith("/api/v1/auth/")
			|| isPushRevokeRequest(request)
			|| requestUri.equals("/v3/api-docs")
			|| requestUri.startsWith("/v3/api-docs/")
			|| requestUri.equals("/v3/api-docs.yaml")
			|| requestUri.equals("/swagger-ui.html")
			|| requestUri.startsWith("/swagger-ui/");
	}

	private boolean isPushRevokeRequest(HttpServletRequest request) {
		if (!request.getMethod().equals("POST")) {
			return false;
		}
		String path = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
			path = path.substring(contextPath.length());
		}
		return path.matches("/api/v1/push-devices/[^/]+/revoke");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			chain.doFilter(request, response);
			return;
		}
		AccessPrincipal principal;
		try {
			principal = accessTokenService.verify(authorization.substring(7));
			if (!isWithdrawalRequest(request) && !isActiveUser(principal.userId())) {
				throw new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
			}
		} catch (BusinessException exception) {
			SecurityContextHolder.clearContext();
			writeUnauthorized(response);
			return;
		}
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(principal, null, List.of())
		);
		chain.doFilter(request, response);
	}

	private boolean isWithdrawalRequest(HttpServletRequest request) {
		String path = request.getRequestURI();
		String contextPath = request.getContextPath();
		if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
			path = path.substring(contextPath.length());
		}
		return request.getMethod().equals("DELETE") && path.equals("/api/v1/users/me");
	}

	private boolean isActiveUser(long userId) {
		return userRepository.findById(userId)
			.filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getEmailVerifiedAt() != null)
			.isPresent();
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
