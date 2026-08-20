package com.openmd.server.auth.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.auth.domain.AuthErrorCode;
import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserRepository;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.security.AccessTokenService;
import com.openmd.server.auth.security.IssuedAccessToken;
import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.global.error.BusinessException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");
	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder passwords = mock(PasswordEncoder.class);
	private final VerificationCodeGenerator codes = mock(VerificationCodeGenerator.class);
	private final VerificationCodeDigest digests = new VerificationCodeDigest(
		"0123456789abcdef0123456789abcdef".getBytes()
	);
	private final EmailVerificationStore verifications = mock(EmailVerificationStore.class);
	private final VerificationEmailSender emails = mock(VerificationEmailSender.class);
	private final RefreshTokenService refreshTokens = mock(RefreshTokenService.class);
	private final AccessTokenService accessTokens = mock(AccessTokenService.class);
	private AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(users, passwords, codes, digests, verifications, emails,
			refreshTokens, accessTokens, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createsOnePendingUserAndSendsAHashedSixCharacterCode() throws Exception {
		when(users.findByNormalizedEmail("learner@example.com")).thenReturn(Optional.empty());
		when(passwords.encode("password1")).thenReturn("argon2-hash");
		when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			setId(user, 11L);
			return user;
		});
		when(codes.generate()).thenReturn("A7K9M2");
		when(verifications.issue(eq(11L), any(), eq(NOW), any(), any(), eq(false)))
			.thenReturn(EmailVerificationStore.IssueResult.success());

		service.signUp(" Learner@Example.COM ", "password1");

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(users).saveAndFlush(userCaptor.capture());
		assertEquals("learner@example.com", userCaptor.getValue().getNormalizedEmail());
		assertEquals("Learner@Example.COM", userCaptor.getValue().getEmail());
		assertEquals("argon2-hash", userCaptor.getValue().getPasswordHash());
		assertEquals(UserStatus.PENDING_ACTIVATION, userCaptor.getValue().getStatus());
		verify(verifications).issue(eq(11L), eq(digests.create(11L, "A7K9M2")), eq(NOW), any(), any(), eq(false));
		verify(emails).sendVerificationCode("Learner@Example.COM", "A7K9M2");
	}

	@Test
	void normalizesSubmittedCodeThenActivatesAndConsumesIt() throws Exception {
		User user = pendingUser(21L);
		when(users.findByNormalizedEmail("learner@example.com")).thenReturn(Optional.of(user));
		when(verifications.verify(21L, digests.create(21L, "A7K9M2")))
			.thenReturn(EmailVerificationStore.VerificationResult.MATCHED);

		service.confirm("Learner@Example.COM", "  a7k9m2  ");

		assertEquals(UserStatus.ACTIVE, user.getStatus());
		assertEquals(NOW, user.getEmailVerifiedAt());
		assertEquals(NOW, user.getActivatedAt());
		verify(verifications).consume(21L);
	}

	@Test
	void fifthOrExpiredCodeCannotActivateTheUser() throws Exception {
		User user = pendingUser(22L);
		when(users.findByNormalizedEmail("learner@example.com")).thenReturn(Optional.of(user));
		when(verifications.verify(eq(22L), any())).thenReturn(EmailVerificationStore.VerificationResult.EXPIRED);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> service.confirm("learner@example.com", "A7K9M2"));

		assertEquals(AuthErrorCode.EXPIRED_VERIFICATION_CODE, exception.getErrorCode());
		assertEquals(UserStatus.PENDING_ACTIVATION, user.getStatus());
		verify(users, never()).save(any());
	}

	@Test
	void inactiveAccountsUseTheSameLoginFailureAsWrongPasswords() throws Exception {
		User user = pendingUser(23L);
		when(users.findByNormalizedEmail("learner@example.com")).thenReturn(Optional.of(user));
		when(passwords.matches("password1", "argon2-hash")).thenReturn(true);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> service.login("learner@example.com", "password1"));

		assertEquals(AuthErrorCode.LOGIN_FAILED, exception.getErrorCode());
		verify(refreshTokens, never()).issue(23L);
	}

	@Test
	void propagatesMailFailureAndCompensatesOnlyTheFailedCode() throws Exception {
		when(users.findByNormalizedEmail("learner@example.com")).thenReturn(Optional.empty());
		when(passwords.encode("password1")).thenReturn("argon2-hash");
		when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
			User user = invocation.getArgument(0);
			setId(user, 25L);
			return user;
		});
		when(codes.generate()).thenReturn("A7K9M2");
		when(verifications.issue(eq(25L), any(), eq(NOW), any(), any(), eq(false)))
			.thenReturn(EmailVerificationStore.IssueResult.success());
		BusinessException deliveryFailure = new BusinessException(AuthErrorCode.EMAIL_DELIVERY_FAILED);
		org.mockito.Mockito.doThrow(deliveryFailure)
			.when(emails).sendVerificationCode("learner@example.com", "A7K9M2");

		BusinessException thrown = assertThrows(BusinessException.class,
			() -> service.signUp("learner@example.com", "password1"));

		assertEquals(AuthErrorCode.EMAIL_DELIVERY_FAILED, thrown.getErrorCode());
		verify(verifications).cancelIssue(25L, digests.create(25L, "A7K9M2"));
	}

	@Test
	void refreshCompletesUserValidationAndAccessTokenIssuanceBeforeConsumingTheRefreshToken() throws Exception {
		User user = activeUser(31L);
		RefreshTokenSession current = new RefreshTokenSession(
			31L,
			"session-id",
			Instant.parse("2026-09-18T00:00:00Z")
		);
		IssuedRefreshToken replacement = new IssuedRefreshToken(
			"session-id.new-secret",
			"session-id",
			current.expiresAt()
		);
		when(refreshTokens.inspect("current-refresh")).thenReturn(current);
		when(users.findById(31L)).thenReturn(Optional.of(user));
		when(accessTokens.issue(31L, "session-id"))
			.thenReturn(new IssuedAccessToken("access-token", NOW.plusSeconds(300)));
		when(refreshTokens.rotate("current-refresh"))
			.thenReturn(new RotatedRefreshToken(31L, replacement));

		SessionTokens result = service.refresh("current-refresh");

		assertEquals("access-token", result.accessToken());
		assertEquals("session-id.new-secret", result.refreshToken());
		InOrder order = inOrder(refreshTokens, users, accessTokens);
		order.verify(refreshTokens).inspect("current-refresh");
		order.verify(users).findById(31L);
		order.verify(accessTokens).issue(31L, "session-id");
		order.verify(refreshTokens).rotate("current-refresh");
	}

	@Test
	void refreshDoesNotConsumeTheTokenWhenUserLookupFails() {
		when(refreshTokens.inspect("current-refresh")).thenReturn(new RefreshTokenSession(
			31L,
			"session-id",
			Instant.parse("2026-09-18T00:00:00Z")
		));
		when(users.findById(31L)).thenThrow(new IllegalStateException("database unavailable"));

		assertThrows(IllegalStateException.class, () -> service.refresh("current-refresh"));

		verify(refreshTokens, never()).rotate("current-refresh");
	}

	@Test
	void refreshDoesNotConsumeTheTokenWhenAccessTokenIssuanceFails() throws Exception {
		User user = activeUser(31L);
		when(refreshTokens.inspect("current-refresh")).thenReturn(new RefreshTokenSession(
			31L,
			"session-id",
			Instant.parse("2026-09-18T00:00:00Z")
		));
		when(users.findById(31L)).thenReturn(Optional.of(user));
		when(accessTokens.issue(31L, "session-id"))
			.thenThrow(new IllegalStateException("signing unavailable"));

		assertThrows(IllegalStateException.class, () -> service.refresh("current-refresh"));

		verify(refreshTokens, never()).rotate("current-refresh");
	}

	private User pendingUser(long id) throws Exception {
		User user = User.pending("learner@example.com", "learner@example.com", "argon2-hash");
		setId(user, id);
		return user;
	}

	private User activeUser(long id) throws Exception {
		User user = pendingUser(id);
		user.activate(NOW);
		return user;
	}

	private static void setId(User user, long id) throws Exception {
		Field field = BaseEntity.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(user, id);
	}
}
