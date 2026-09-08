package com.openmd.server.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.auth.repository.RefreshSessionStore;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.push.domain.PushPermission;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.dto.command.RegisterPushDeviceCommand;
import com.openmd.server.push.dto.command.RevokePushDeviceCommand;
import com.openmd.server.push.dto.response.PushDeviceRegistrationResult;
import com.openmd.server.push.domain.PushDeviceStatus;
import com.openmd.server.push.error.PushErrorCode;
import com.openmd.server.push.repository.PushRateLimitStore;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class PushDeviceServiceTest {

  private static final String INSTALLATION_ID = "11111111-1111-4111-8111-111111111111";
  private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
  private static final String OPERATION_ID = "33333333-3333-4333-8333-333333333333";
  private static final Instant NOW = Instant.parse("2026-09-06T06:00:00Z");
  private final PushDeviceTransaction transaction = mock(PushDeviceTransaction.class);
  private final PushRateLimitStore rateLimits = mock(PushRateLimitStore.class);
  private PushDeviceService service;
  private final RefreshSessionStore sessions = mock(RefreshSessionStore.class);
  private final PushDeviceLifecycle lifecycle = mock(PushDeviceLifecycle.class);

  @BeforeEach
  void setUp() {
    service = new PushDeviceService(transaction, rateLimits, sessions, lifecycle);
    when(sessions.isActive("session-42", 42L)).thenReturn(true);
  }

  @Test
  void rejectsRegistrationAfterSessionHasEndedWithoutCreatingADevice() {
    when(sessions.isActive("session-42", 42L)).thenReturn(false);
    RegisterPushDeviceCommand command = registerCommand();
    BusinessException error = assertThrows(BusinessException.class,
        () -> service.register(42L, "session-42", command));
    assertEquals(AuthErrorCode.INVALID_CREDENTIAL, error.getErrorCode());
    verify(transaction, never()).register(42L, "session-42", command);
  }

  @Test
  void logoutDuringFirstRegistrationCompensatesAfterCommitBeforeReturning() {
    when(sessions.isActive("session-42", 42L)).thenReturn(true, false);
    RegisterPushDeviceCommand command = registerCommand();
    BusinessException error = assertThrows(BusinessException.class,
        () -> service.register(42L, "session-42", command));
    assertEquals(AuthErrorCode.INVALID_CREDENTIAL, error.getErrorCode());
    var order = org.mockito.Mockito.inOrder(transaction, lifecycle);
    order.verify(transaction).register(42L, "session-42", command);
    order.verify(lifecycle).revokeSession("session-42");
  }

  @Test
  void checksBothAccountAndInstallationLimitsBeforeRegistering() {
    RegisterPushDeviceCommand command = registerCommand();
    PushDeviceRegistrationResult expected =
        new PushDeviceRegistrationResult(
            INSTALLATION_ID,
            1L,
            "44444444-4444-4444-8444-444444444444",
            PushDeviceStatus.ACTIVE,
            42L);
    when(rateLimits.consume("account", "42", 60, 60)).thenReturn(0L);
    when(rateLimits.consume("installation", INSTALLATION_ID, 20, 60)).thenReturn(0L);
    when(transaction.register(42L, "session-42", command)).thenReturn(expected);

    assertEquals(expected, service.register(42L, "session-42", command));

    verify(rateLimits).consume("account", "42", 60, 60);
    verify(rateLimits).consume("installation", INSTALLATION_ID, 20, 60);
  }

  @Test
  void rateLimitFailureStopsBeforeTheDatabaseMutation() {
    RegisterPushDeviceCommand command = registerCommand();
    when(rateLimits.consume("account", "42", 60, 60)).thenReturn(17L);

    BusinessException failure =
        assertThrows(
            BusinessException.class, () -> service.register(42L, "session-42", command));

    assertEquals(PushErrorCode.RATE_LIMITED, failure.getErrorCode());
    verify(transaction, never()).register(42L, "session-42", command);
  }

  @Test
  void redisFailureFailsClosedAsA503CommonError() {
    RegisterPushDeviceCommand command = registerCommand();
    when(rateLimits.consume("account", "42", 60, 60))
        .thenThrow(new QueryTimeoutException("redis timeout"));

    BusinessException failure =
        assertThrows(
            BusinessException.class, () -> service.register(42L, "session-42", command));

    assertEquals(PushErrorCode.DEPENDENCY_UNAVAILABLE, failure.getErrorCode());
    assertEquals(503, failure.getErrorCode().status().value());
    assertEquals("COMMON_999", failure.getErrorCode().code());
  }

  @Test
  void anonymousRevokeUsesOnlyTheInstallationLimit() {
    RevokePushDeviceCommand command =
        new RevokePushDeviceCommand(
            INSTALLATION_ID,
            KEY,
            OPERATION_ID,
            NOW,
            "44444444-4444-4444-8444-444444444444",
            1L);
    when(rateLimits.consume("installation", INSTALLATION_ID, 20, 60)).thenReturn(0L);

    service.revoke(command);

    verify(rateLimits).consume("installation", INSTALLATION_ID, 20, 60);
    verify(rateLimits, never()).consume("account", "42", 60, 60);
    verify(transaction).revoke(command);
  }

  private RegisterPushDeviceCommand registerCommand() {
    return new RegisterPushDeviceCommand(
        INSTALLATION_ID,
        KEY,
        OPERATION_ID,
        NOW,
        0L,
        PushPlatform.IOS,
        PushProvider.EXPO,
        "ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]",
        PushPermission.GRANTED);
  }
}
