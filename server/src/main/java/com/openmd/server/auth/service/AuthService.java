package com.openmd.server.auth.service;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.dto.model.IssuedRefreshToken;
import com.openmd.server.auth.dto.model.RefreshTokenSession;
import com.openmd.server.auth.dto.model.RotatedRefreshToken;
import com.openmd.server.auth.dto.response.CurrentUser;
import com.openmd.server.auth.dto.response.SessionTokens;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.auth.security.AccessTokenService;
import com.openmd.server.auth.security.IssuedAccessToken;
import com.openmd.server.auth.util.EmailNormalizer;
import com.openmd.server.auth.util.NicknameNormalizer;
import com.openmd.server.global.error.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;
	private final AccessTokenService accessTokenService;

	public AuthService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		RefreshTokenService refreshTokenService,
		AccessTokenService accessTokenService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
		this.accessTokenService = accessTokenService;
	}

	public SessionTokens login(String email, String password) {
		User user = userRepository.findByNormalizedEmail(EmailNormalizer.normalize(email))
			.orElseThrow(this::loginFailed);
		if (!passwordEncoder.matches(password, user.getPasswordHash())
			|| user.getStatus() != UserStatus.ACTIVE
			|| user.getEmailVerifiedAt() == null) {
			throw loginFailed();
		}
		IssuedRefreshToken refresh = refreshTokenService.issue(user.getId());
		IssuedAccessToken access = accessTokenService.issue(user.getId(), refresh.sessionId());
		return sessionTokens(access, refresh);
	}

	public SessionTokens refresh(String refreshToken) {
		RefreshTokenSession current = refreshTokenService.inspect(refreshToken);
		User user = userRepository.findById(current.userId()).orElseThrow(this::invalidCredential);
		if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
			refreshTokenService.revoke(refreshToken);
			throw invalidCredential();
		}
		IssuedAccessToken access = accessTokenService.issue(user.getId(), current.sessionId());
		RotatedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
		return sessionTokens(access, rotated.refreshToken());
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	@Transactional(readOnly = true)
	public CurrentUser currentUser(long userId) {
		return toCurrentUser(activeUser(userId));
	}

	@Transactional
	public CurrentUser updateNickname(long userId, String input) {
		User user = activeUser(userId);
		String nickname = NicknameNormalizer.normalize(input);
		if (userRepository.existsByNicknameIgnoreCaseAndIdNot(nickname, userId)) {
			throw new BusinessException(AuthErrorCode.NICKNAME_CONFLICT);
		}
		user.updateNickname(nickname);
		try {
			userRepository.flush();
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(AuthErrorCode.NICKNAME_CONFLICT);
		}
		return toCurrentUser(user);
	}

	private User activeUser(long userId) {
		User user = userRepository.findById(userId).orElseThrow(this::invalidCredential);
		if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
			throw invalidCredential();
		}
		return user;
	}

	private CurrentUser toCurrentUser(User user) {
		return new CurrentUser(user.getId(), user.getEmail(), user.getNickname(), true, user.getStatus());
	}

	private SessionTokens sessionTokens(IssuedAccessToken access, IssuedRefreshToken refresh) {
		return new SessionTokens(access.token(), access.expiresAt(), refresh.token(), refresh.expiresAt());
	}

	private BusinessException loginFailed() {
		return new BusinessException(AuthErrorCode.LOGIN_FAILED);
	}

	private BusinessException invalidCredential() {
		return new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
	}
}
