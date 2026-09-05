package com.openmd.server.auth.service;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.dto.response.AccountWithdrawalResult;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.TransactionException;

public final class AccountWithdrawalService {

	private static final Logger log = LoggerFactory.getLogger(AccountWithdrawalService.class);
	private static final String CONFIRMATION = "회원탈퇴";
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final RefreshTokenService refreshTokenService;
	private final Clock clock;
	private final TransactionOperations transactions;

	public AccountWithdrawalService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		RefreshTokenService refreshTokenService,
		Clock clock,
		TransactionOperations transactions
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.refreshTokenService = refreshTokenService;
		this.clock = clock;
		this.transactions = transactions;
	}

	public AccountWithdrawalResult withdraw(
		long userId,
		String withdrawalRequestId,
		String currentPassword,
		String confirmation
	) {
		UUID requestId = parseRequestId(withdrawalRequestId);
		if (!CONFIRMATION.equals(confirmation)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT);
		}

		AccountWithdrawalResult result;
		try {
			result = transactions.execute(status -> withdrawInTransaction(
				userId, requestId, currentPassword
			));
		} catch (DataAccessException | TransactionException exception) {
			throw new BusinessException(AuthErrorCode.WITHDRAWAL_PERSISTENCE_FAILED);
		}
		if (result == null) {
			throw new BusinessException(AuthErrorCode.WITHDRAWAL_PERSISTENCE_FAILED);
		}

		revokeSessions(userId);
		return result;
	}

	private AccountWithdrawalResult withdrawInTransaction(
		long userId,
		UUID requestId,
		String currentPassword
	) {
		User user = userRepository.findByIdForUpdate(userId)
			.orElseThrow(this::invalidCredential);
		if (user.getStatus() == UserStatus.WITHDRAWN) {
			if (requestId.equals(user.getWithdrawalRequestId())) {
				return result(user);
			}
			throw invalidCredential();
		}
		if ((user.getStatus() != UserStatus.ACTIVE && user.getStatus() != UserStatus.SUSPENDED)
			|| user.getEmailVerifiedAt() == null) {
			throw invalidCredential();
		}
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new BusinessException(AuthErrorCode.WITHDRAWAL_PASSWORD_MISMATCH);
		}
		user.withdraw(requestId, clock.instant());
		userRepository.flush();
		return result(user);
	}

	private AccountWithdrawalResult result(User user) {
		return new AccountWithdrawalResult(
			user.getWithdrawalRequestId(),
			user.getStatus(),
			user.getWithdrawnAt(),
			user.getWithdrawalDisposalDueAt()
		);
	}

	private UUID parseRequestId(String input) {
		try {
			UUID requestId = UUID.fromString(input);
			if (input.length() != 36 || !requestId.toString().equalsIgnoreCase(input)) {
				throw new IllegalArgumentException("non-canonical UUID");
			}
			return requestId;
		} catch (RuntimeException exception) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT);
		}
	}

	private void revokeSessions(long userId) {
		for (int attempt = 1; attempt <= 2; attempt++) {
			try {
				refreshTokenService.revokeAll(userId);
				return;
			} catch (RuntimeException exception) {
				if (attempt == 2) {
					log.warn("Account withdrawal committed but refresh session cleanup failed for userId={}", userId);
				}
			}
		}
	}

	private BusinessException invalidCredential() {
		return new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
	}
}
