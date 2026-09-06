package com.openmd.server.push.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.push.domain.PushDevice;
import com.openmd.server.push.domain.PushDeviceOperation;
import com.openmd.server.push.domain.PushDeviceStatus;
import com.openmd.server.push.domain.PushPermission;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.dto.command.RegisterPushDeviceCommand;
import com.openmd.server.push.dto.command.RevokePushDeviceCommand;
import com.openmd.server.push.dto.response.PushDeviceRegistrationResult;
import com.openmd.server.push.dto.response.PushDeviceStatusResult;
import com.openmd.server.push.error.PushErrorCode;
import com.openmd.server.push.repository.PushDeviceOperationRepository;
import com.openmd.server.push.repository.PushDeviceRepository;
import com.openmd.server.push.security.PushInstallationCredential;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PushDeviceTransactionTest {

  private static final Instant NOW = Instant.parse("2026-09-06T06:00:00Z");
  private static final String INSTALLATION_ID = "11111111-1111-4111-8111-111111111111";
  private static final String OTHER_INSTALLATION_ID = "22222222-2222-4222-8222-222222222222";
  private static final String OPERATION_ID = "33333333-3333-4333-8333-333333333333";
  private static final String BINDING_ID = "44444444-4444-4444-8444-444444444444";
  private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
  private static final String TOKEN = "ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]";

  private final PushDeviceRepository devices = mock(PushDeviceRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final PushDeviceOperationRepository operations =
      mock(PushDeviceOperationRepository.class);
  private final PushInstallationCredential credentials = new PushInstallationCredential();
  private final EntityManager entityManager = mock(EntityManager.class);
  private PushDeviceTransaction transaction;

  @BeforeEach
  void setUp() {
    User active = mock(User.class);
    when(active.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(active.getEmailVerifiedAt()).thenReturn(NOW.minusSeconds(60));
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(active));
    transaction =
        new PushDeviceTransaction(
            users,
            devices,
            operations,
            credentials,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> UUID.fromString(BINDING_ID),
            entityManager);
  }

  @Test
  void registrationRechecksAndLocksTheActiveUserInsideTheDeviceTransaction() {
    User withdrawn = mock(User.class);
    when(withdrawn.getStatus()).thenReturn(UserStatus.WITHDRAWN);
    when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(withdrawn));

    BusinessException failure =
        assertThrows(
            BusinessException.class,
            () -> transaction.register(42L, "session-42", registerCommand(0, NOW)));

    assertEquals(AuthErrorCode.INVALID_CREDENTIAL, failure.getErrorCode());
    verify(devices, never()).findByInstallationId(any());
  }

  @Test
  void createsAnActiveDeviceAndStoresASecretFreeIdempotentResult() {
    when(devices.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());
    when(devices.findByProviderAndPushTokenDigest(any(), any())).thenReturn(Optional.empty());

    PushDeviceRegistrationResult result =
        transaction.register(42L, "session-42", registerCommand(0, NOW));

    assertEquals(INSTALLATION_ID, result.installationId());
    assertEquals(1L, result.revision());
    assertEquals(BINDING_ID, result.bindingId());
    assertEquals(PushDeviceStatus.ACTIVE, result.status());
    assertEquals(42L, result.userId());

    ArgumentCaptor<PushDevice> device = ArgumentCaptor.forClass(PushDevice.class);
    verify(devices).save(device.capture());
    assertEquals(credentials.digestInstallationKey(KEY), device.getValue().getInstallationKeyDigest());
    assertEquals(credentials.digestPushToken(TOKEN), device.getValue().getPushTokenDigest());
    assertEquals(TOKEN, device.getValue().getPushToken());

    ArgumentCaptor<PushDeviceOperation> operation =
        ArgumentCaptor.forClass(PushDeviceOperation.class);
    verify(operations).save(operation.capture());
    assertFalse(operation.getValue().getRequestDigest().contains(TOKEN));
    assertTrue(
        Arrays.stream(PushDeviceOperation.class.getDeclaredFields())
            .noneMatch(field -> field.getName().equals("pushToken")));
  }

  @Test
  void returnsTheStoredResultForTheSameOperationWithoutMutatingTheDeviceAgain() {
    PushDevice existing = activeDevice(INSTALLATION_ID, 42L, "session-42", BINDING_ID, TOKEN);
    PushDeviceRegistrationResult stored =
        new PushDeviceRegistrationResult(
            INSTALLATION_ID, 1L, BINDING_ID, PushDeviceStatus.ACTIVE, 42L);
    PushDeviceOperation operation =
        PushDeviceOperation.successfulRegistration(
            INSTALLATION_ID,
            OPERATION_ID,
            42L,
            transaction.requestDigest(registerCommand(1, NOW)),
            NOW,
            stored,
            NOW);
    when(devices.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(existing));
    when(devices.findAllByInstallationIdInForUpdate(java.util.List.of(INSTALLATION_ID)))
        .thenReturn(java.util.List.of(existing));
    when(operations.findByInstallationIdAndOperationId(INSTALLATION_ID, OPERATION_ID))
        .thenReturn(Optional.of(operation));

    PushDeviceRegistrationResult replay =
        transaction.register(42L, "session-42", registerCommand(1, NOW));

    assertEquals(stored, replay);
    assertEquals(1L, existing.getRevision());
    verify(entityManager).detach(existing);
    verify(devices, never()).save(any());
  }

  @Test
  void rejectsExpiredFutureAndStaleRevisionOperationsWithoutChangingState() {
    PushDevice existing = activeDevice(INSTALLATION_ID, 42L, "session-42", BINDING_ID, TOKEN);
    when(devices.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(existing));
    when(devices.findAllByInstallationIdInForUpdate(java.util.List.of(INSTALLATION_ID)))
        .thenReturn(java.util.List.of(existing));

    BusinessException expired =
        assertThrows(
            BusinessException.class,
            () -> transaction.register(42L, "session-42", registerCommand(1, NOW.minusSeconds(86400))));
    BusinessException future =
        assertThrows(
            BusinessException.class,
            () -> transaction.register(42L, "session-42", registerCommand(1, NOW.plusSeconds(301))));
    BusinessException stale =
        assertThrows(
            BusinessException.class,
            () -> transaction.register(42L, "session-42", registerCommand(0, NOW)));

    assertEquals(PushErrorCode.OPERATION_EXPIRED, expired.getErrorCode());
    assertEquals(PushErrorCode.OPERATION_EXPIRED, future.getErrorCode());
    assertEquals(PushErrorCode.REVISION_CONFLICT, stale.getErrorCode());
    assertEquals(1L, existing.getRevision());
  }

  @Test
  void acceptsAnOperationIssuedAtExactlyTheFiveMinuteFutureSkewBoundary() {
    when(devices.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());
    when(devices.findByProviderAndPushTokenDigest(any(), any())).thenReturn(Optional.empty());

    PushDeviceRegistrationResult result =
        transaction.register(42L, "session-42", registerCommand(0, NOW.plusSeconds(300)));

    assertEquals(PushDeviceStatus.ACTIVE, result.status());
  }

  @Test
  void anotherUserCannotReplayAnExistingOperationEvenWithTheInstallationKey() {
    User other = mock(User.class);
    when(other.getStatus()).thenReturn(UserStatus.ACTIVE);
    when(other.getEmailVerifiedAt()).thenReturn(NOW.minusSeconds(60));
    when(users.findByIdForUpdate(84L)).thenReturn(Optional.of(other));
    PushDevice existing = activeDevice(INSTALLATION_ID, 42L, "session-42", BINDING_ID, TOKEN);
    PushDeviceRegistrationResult stored =
        new PushDeviceRegistrationResult(
            INSTALLATION_ID, 1L, BINDING_ID, PushDeviceStatus.ACTIVE, 42L);
    PushDeviceOperation operation =
        PushDeviceOperation.successfulRegistration(
            INSTALLATION_ID,
            OPERATION_ID,
            42L,
            transaction.requestDigest(registerCommand(1, NOW)),
            NOW,
            stored,
            NOW);
    when(devices.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(existing));
    when(devices.findAllByInstallationIdInForUpdate(java.util.List.of(INSTALLATION_ID)))
        .thenReturn(java.util.List.of(existing));
    when(operations.findByInstallationIdAndOperationId(INSTALLATION_ID, OPERATION_ID))
        .thenReturn(Optional.of(operation));

    BusinessException failure =
        assertThrows(
            BusinessException.class,
            () -> transaction.register(84L, "session-84", registerCommand(1, NOW)));

    assertEquals(PushErrorCode.OPERATION_CONFLICT, failure.getErrorCode());
  }

  @Test
  void atomicallyMovesTheSameUsersTokenToANewInstallationWithoutTouchingOtherDevices() {
    PushDevice previous = activeDevice(OTHER_INSTALLATION_ID, 42L, "old-session", BINDING_ID, TOKEN);
    when(devices.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());
    when(devices.findByProviderAndPushTokenDigest(
            PushProvider.EXPO, credentials.digestPushToken(TOKEN)))
        .thenReturn(Optional.of(previous));
    when(devices.findAllByInstallationIdInForUpdate(java.util.List.of(OTHER_INSTALLATION_ID)))
        .thenReturn(java.util.List.of(previous));

    PushDeviceRegistrationResult result =
        transaction.register(42L, "new-session", registerCommand(0, NOW));

    assertEquals(PushDeviceStatus.REVOKED, previous.getStatus());
    assertNull(previous.getPushToken());
    assertEquals(2L, previous.getRevision());
    assertEquals(PushDeviceStatus.ACTIVE, result.status());
  }

  @Test
  void refusesToTakeATokenFromAnotherUser() {
    PushDevice previous = activeDevice(OTHER_INSTALLATION_ID, 84L, "other-session", BINDING_ID, TOKEN);
    when(devices.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());
    when(devices.findByProviderAndPushTokenDigest(
            PushProvider.EXPO, credentials.digestPushToken(TOKEN)))
        .thenReturn(Optional.of(previous));
    when(devices.findAllByInstallationIdInForUpdate(java.util.List.of(OTHER_INSTALLATION_ID)))
        .thenReturn(java.util.List.of(previous));

    BusinessException failure =
        assertThrows(
            BusinessException.class,
            () -> transaction.register(42L, "new-session", registerCommand(0, NOW)));

    assertEquals(PushErrorCode.TOKEN_CONFLICT, failure.getErrorCode());
    assertEquals(PushDeviceStatus.ACTIVE, previous.getStatus());
    verify(devices, never()).save(any());
  }

  @Test
  void aLateRevokeForAnOldBindingIsASuccessfulNoOp() {
    PushDevice current = activeDevice(INSTALLATION_ID, 42L, "new-session", BINDING_ID, TOKEN);
    when(devices.findByInstallationIdForUpdate(INSTALLATION_ID))
        .thenReturn(Optional.of(current));

    boolean revoked =
        transaction
            .revoke(
                new RevokePushDeviceCommand(
                    INSTALLATION_ID,
                    KEY,
                    OPERATION_ID,
                    NOW,
                    "55555555-5555-4555-8555-555555555555",
                    1L))
            .revoked();

    assertFalse(revoked);
    assertEquals(PushDeviceStatus.ACTIVE, current.getStatus());
  }

  @Test
  void statusLookupNeverExposesAnotherAccountsBinding() {
    PushDevice current = activeDevice(INSTALLATION_ID, 84L, "session-84", BINDING_ID, TOKEN);
    when(devices.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(current));

    PushDeviceStatusResult result = transaction.status(42L, INSTALLATION_ID, KEY);

    assertFalse(result.belongsToCurrentUser());
    assertNull(result.bindingId());
    assertEquals(1L, result.revision());
  }

  private RegisterPushDeviceCommand registerCommand(long revision, Instant issuedAt) {
    return new RegisterPushDeviceCommand(
        INSTALLATION_ID,
        KEY,
        OPERATION_ID,
        issuedAt,
        revision,
        PushPlatform.IOS,
        PushProvider.EXPO,
        TOKEN,
        PushPermission.GRANTED);
  }

  private PushDevice activeDevice(
      String installationId, long userId, String sessionId, String bindingId, String token) {
    return PushDevice.registered(
        installationId,
        credentials.digestInstallationKey(KEY),
        userId,
        sessionId,
        bindingId,
        PushPlatform.IOS,
        PushProvider.EXPO,
        token,
        credentials.digestPushToken(token));
  }
}
