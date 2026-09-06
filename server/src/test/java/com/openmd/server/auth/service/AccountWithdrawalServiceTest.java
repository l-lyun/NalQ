package com.openmd.server.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.dto.response.AccountWithdrawalResult;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.push.service.PushDeviceLifecycle;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class AccountWithdrawalServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-03T06:00:00Z");
	private static final UUID REQUEST_ID = UUID.fromString("018f5f95-61c7-7d7b-9f8c-6cb4a9b16731");
	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder passwords = mock(PasswordEncoder.class);
	private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
	private final TransactionOperations transactions = mock(TransactionOperations.class);
	private final PushDeviceLifecycle pushDevices = mock(PushDeviceLifecycle.class);
	private AccountWithdrawalService service;

	@BeforeEach
	void setUp() {
		org.mockito.Mockito.doAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		}).when(transactions).execute(any());
		service = new AccountWithdrawalService(
			users,
			passwords,
			refreshTokens,
			pushDevices,
			Clock.fixed(NOW, ZoneOffset.UTC),
			transactions
		);
	}

	@Test
	void withdrawsTheActiveAccountAndReleasesDirectIdentifiersBeforeRevokingAllSessions() throws Exception {
		User user = activeUser(42L);
		when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(passwords.matches("password1", "argon2-hash")).thenReturn(true);

		AccountWithdrawalResult result = service.withdraw(
			42L, REQUEST_ID.toString(), "password1", "회원탈퇴"
		);

		assertEquals(REQUEST_ID, result.withdrawalRequestId());
		assertEquals(UserStatus.WITHDRAWN, result.status());
		assertEquals(NOW, result.withdrawnAt());
		assertEquals(NOW.plusSeconds(30L * 24 * 60 * 60), result.dataDisposalDeadline());
		assertEquals(UserStatus.WITHDRAWN, user.getStatus());
		assertNull(user.getEmail());
		assertNull(user.getNormalizedEmail());
		assertNull(user.getPasswordHash());
		assertNull(user.getNickname());
		verify(users).flush();
		verify(pushDevices).deleteForUser(42L);
		verify(refreshTokens).revokeAll(42L);
	}

	@Test
	void withdrawsASuspendedAccountAfterTheSameIdentityChecks() throws Exception {
		User user = activeUser(42L);
		Field status = User.class.getDeclaredField("status");
		status.setAccessible(true);
		status.set(user, UserStatus.SUSPENDED);
		when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(passwords.matches("password1", "argon2-hash")).thenReturn(true);

		AccountWithdrawalResult result = service.withdraw(
			42L, REQUEST_ID.toString(), "password1", "회원탈퇴"
		);

		assertEquals(UserStatus.WITHDRAWN, result.status());
		verify(users).flush();
		verify(refreshTokens).revokeAll(42L);
	}

	@Test
	void replaysTheOriginalResultForTheSameRequestWithoutRecheckingTheRemovedPassword() throws Exception {
		User user = activeUser(42L);
		user.withdraw(REQUEST_ID, NOW);
		when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));

		AccountWithdrawalResult result = service.withdraw(
			42L, REQUEST_ID.toString(), "discarded-after-first-request", "회원탈퇴"
		);

		assertEquals(NOW, result.withdrawnAt());
		verify(passwords, never()).matches(any(), any());
		verify(refreshTokens).revokeAll(42L);
	}

	@Test
	void rejectsWrongPasswordWithoutChangingOrRevokingAnything() throws Exception {
		User user = activeUser(42L);
		when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(passwords.matches("wrong-password", "argon2-hash")).thenReturn(false);

		BusinessException failure = assertThrows(BusinessException.class, () -> service.withdraw(
			42L, REQUEST_ID.toString(), "wrong-password", "회원탈퇴"
		));

		assertEquals(AuthErrorCode.WITHDRAWAL_PASSWORD_MISMATCH, failure.getErrorCode());
		assertEquals(UserStatus.ACTIVE, user.getStatus());
		verify(users, never()).flush();
		verify(refreshTokens, never()).revokeAll(org.mockito.ArgumentMatchers.anyLong());
		verify(pushDevices, never()).deleteForUser(org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void rejectsAnythingOtherThanTheExactConfirmationAndAUuid() throws Exception {
		User user = activeUser(42L);
		when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));

		BusinessException confirmationFailure = assertThrows(BusinessException.class, () -> service.withdraw(
			42L, REQUEST_ID.toString(), "password1", " 회원탈퇴"
		));
		BusinessException requestIdFailure = assertThrows(BusinessException.class, () -> service.withdraw(
			42L, "not-a-uuid", "password1", "회원탈퇴"
		));
		BusinessException shortenedUuidFailure = assertThrows(BusinessException.class, () -> service.withdraw(
			42L, "1-1-1-1-1", "password1", "회원탈퇴"
		));

		assertEquals(CommonErrorCode.INVALID_INPUT, confirmationFailure.getErrorCode());
		assertEquals(CommonErrorCode.INVALID_INPUT, requestIdFailure.getErrorCode());
		assertEquals(CommonErrorCode.INVALID_INPUT, shortenedUuidFailure.getErrorCode());
		verify(passwords, never()).matches(any(), any());
	}

	@Test
	void retriesRefreshSessionCleanupOnceAfterTheDatabaseCommit() throws Exception {
		User user = activeUser(42L);
		when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(user));
		when(passwords.matches("password1", "argon2-hash")).thenReturn(true);
		doThrow(new IllegalStateException("redis unavailable"))
			.doNothing()
			.when(refreshTokens).revokeAll(42L);

		AccountWithdrawalResult result = service.withdraw(
			42L, REQUEST_ID.toString(), "password1", "회원탈퇴"
		);

		assertEquals(UserStatus.WITHDRAWN, result.status());
		verify(refreshTokens, org.mockito.Mockito.times(2)).revokeAll(42L);
	}

	private User activeUser(long id) throws Exception {
		User user = User.active(
			"learner@example.com",
			"learner@example.com",
			"argon2-hash",
			"Study7",
			NOW.minusSeconds(30),
			"TEMP-2026-08-20",
			"TEMP-2026-08-20",
			NOW.minusSeconds(10)
		);
		Field field = BaseEntity.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(user, id);
		return user;
	}
}
