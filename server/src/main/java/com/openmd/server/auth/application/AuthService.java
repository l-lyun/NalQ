package com.openmd.server.auth.application;

import com.openmd.server.auth.domain.AuthErrorCode;
import com.openmd.server.auth.domain.PasswordPolicy;
import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserRepository;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.security.AccessTokenService;
import com.openmd.server.auth.security.IssuedAccessToken;
import com.openmd.server.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class AuthService {

	private static final Duration VERIFICATION_TTL = Duration.ofMinutes(10);
	private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
	private static final String CODE_REGEX = "[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final VerificationCodeGenerator codeGenerator;
	private final VerificationCodeDigest codeDigest;
	private final EmailVerificationStore verificationStore;
	private final VerificationEmailSender emailSender;
	private final RefreshTokenService refreshTokenService;
	private final AccessTokenService accessTokenService;
	private final Clock clock;

	public AuthService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		VerificationCodeGenerator codeGenerator,
		VerificationCodeDigest codeDigest,
		EmailVerificationStore verificationStore,
		VerificationEmailSender emailSender,
		RefreshTokenService refreshTokenService,
		AccessTokenService accessTokenService,
		Clock clock
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.codeGenerator = codeGenerator;
		this.codeDigest = codeDigest;
		this.verificationStore = verificationStore;
		this.emailSender = emailSender;
		this.refreshTokenService = refreshTokenService;
		this.accessTokenService = accessTokenService;
		this.clock = clock;
	}

	public void signUp(String email, String password) {
		if (!PasswordPolicy.isValid(password)) {
			throw new IllegalArgumentException("Password policy must be validated at the API boundary");
		}
		String displayEmail = email.trim();
		String normalizedEmail = EmailNormalizer.normalize(email);
		User user = userRepository.findByNormalizedEmail(normalizedEmail).orElse(null);
		boolean enforceCooldown = user != null;
		if (user == null) {
			try {
				user = userRepository.saveAndFlush(User.pending(
					displayEmail,
					normalizedEmail,
					passwordEncoder.encode(password)
				));
			} catch (DataIntegrityViolationException exception) {
				user = userRepository.findByNormalizedEmail(normalizedEmail).orElseThrow(() -> exception);
				enforceCooldown = true;
			}
		}
		if (user.getStatus() == UserStatus.PENDING_ACTIVATION) {
			issueAndSend(user, enforceCooldown);
		}
	}

	public void resend(String email) {
		String normalizedEmail = EmailNormalizer.normalize(email);
		userRepository.findByNormalizedEmail(normalizedEmail)
			.filter(user -> user.getStatus() == UserStatus.PENDING_ACTIVATION)
			.ifPresent(user -> issueAndSend(user, true));
	}

	@Transactional
	public void confirm(String email, String submittedCode) {
		String code = submittedCode == null ? "" : submittedCode.trim().toUpperCase(Locale.ROOT);
		if (!code.matches(CODE_REGEX)) {
			throw new BusinessException(AuthErrorCode.INVALID_VERIFICATION_CODE);
		}
		User user = userRepository.findByNormalizedEmail(EmailNormalizer.normalize(email))
			.orElseThrow(() -> new BusinessException(AuthErrorCode.EXPIRED_VERIFICATION_CODE));
		if (user.getStatus() == UserStatus.ACTIVE) {
			return;
		}
		if (user.getStatus() != UserStatus.PENDING_ACTIVATION) {
			throw new BusinessException(AuthErrorCode.EXPIRED_VERIFICATION_CODE);
		}
		EmailVerificationStore.VerificationResult result = verificationStore.verify(
			user.getId(),
			codeDigest.create(user.getId(), code)
		);
		if (result == EmailVerificationStore.VerificationResult.MISMATCHED) {
			throw new BusinessException(AuthErrorCode.INVALID_VERIFICATION_CODE);
		}
		if (result == EmailVerificationStore.VerificationResult.EXPIRED) {
			throw new BusinessException(AuthErrorCode.EXPIRED_VERIFICATION_CODE);
		}
		user.activate(clock.instant());
		userRepository.save(user);
		consumeAfterCommit(user.getId());
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
		RotatedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
		User user = userRepository.findById(rotated.userId()).orElseThrow(this::invalidCredential);
		if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
			refreshTokenService.revoke(rotated.refreshToken().token());
			throw invalidCredential();
		}
		IssuedAccessToken access = accessTokenService.issue(user.getId(), rotated.refreshToken().sessionId());
		return sessionTokens(access, rotated.refreshToken());
	}

	public void logout(String refreshToken) {
		refreshTokenService.revoke(refreshToken);
	}

	@Transactional(readOnly = true)
	public CurrentUser currentUser(long userId) {
		User user = userRepository.findById(userId).orElseThrow(this::invalidCredential);
		if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
			throw invalidCredential();
		}
		return new CurrentUser(user.getId(), user.getEmail(), true, user.getStatus());
	}

	private void issueAndSend(User user, boolean enforceCooldown) {
		String code = codeGenerator.generate();
		String digest = codeDigest.create(user.getId(), code);
		EmailVerificationStore.IssueResult result = verificationStore.issue(
			user.getId(),
			digest,
			clock.instant(),
			VERIFICATION_TTL,
			RESEND_COOLDOWN,
			enforceCooldown
		);
		if (!result.issued()) {
			return;
		}
		try {
			emailSender.sendVerificationCode(user.getEmail(), code);
		} catch (BusinessException exception) {
			if (exception.getErrorCode() == AuthErrorCode.EMAIL_DELIVERY_FAILED) {
				try {
					verificationStore.cancelIssue(user.getId(), digest);
				} catch (RuntimeException compensationFailure) {
					exception.addSuppressed(compensationFailure);
				}
			}
			throw exception;
		}
	}

	private void consumeAfterCommit(long userId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			verificationStore.consume(userId);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				try {
					verificationStore.consume(userId);
				} catch (RuntimeException ignored) {
					// The state has its own short TTL; an activated user remains authoritative.
				}
			}
		});
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
