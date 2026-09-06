package com.openmd.server.push.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.error.AuthErrorCode;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.push.domain.PushDevice;
import com.openmd.server.push.domain.PushDeviceOperation;
import com.openmd.server.push.domain.PushDeviceStatus;
import com.openmd.server.push.domain.PushPermission;
import com.openmd.server.push.dto.command.RegisterPushDeviceCommand;
import com.openmd.server.push.dto.command.RevokePushDeviceCommand;
import com.openmd.server.push.dto.response.PushDeviceRegistrationResult;
import com.openmd.server.push.dto.response.PushDeviceRevokeResult;
import com.openmd.server.push.dto.response.PushDeviceStatusResult;
import com.openmd.server.push.error.PushErrorCode;
import com.openmd.server.push.repository.PushDeviceOperationRepository;
import com.openmd.server.push.repository.PushDeviceRepository;
import com.openmd.server.push.security.PushInstallationCredential;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@ConditionalOnProperty(name = "openmd.push.registration-enabled", havingValue = "true")
public class PushDeviceTransaction {

  private static final Duration OPERATION_LIFETIME = Duration.ofHours(24);
  private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
  private static final Pattern EXPO_TOKEN =
      Pattern.compile("^(?:ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]{1,440}]$");

  private final PushDeviceRepository devices;
  private final UserRepository users;
  private final PushDeviceOperationRepository operations;
  private final PushInstallationCredential credentials;
  private final Clock clock;
  private final PushBindingIdSupplier bindingIds;
  private final EntityManager entityManager;

  @Autowired
  public PushDeviceTransaction(
      UserRepository users,
      PushDeviceRepository devices,
      PushDeviceOperationRepository operations,
      PushInstallationCredential credentials,
      ObjectProvider<Clock> clock,
      PushBindingIdSupplier bindingIds,
      EntityManager entityManager) {
    this(
        users,
        devices,
        operations,
        credentials,
        clock.getIfAvailable(Clock::systemUTC),
        bindingIds,
        entityManager);
  }

  PushDeviceTransaction(
      UserRepository users,
      PushDeviceRepository devices,
      PushDeviceOperationRepository operations,
      PushInstallationCredential credentials,
      Clock clock,
      PushBindingIdSupplier bindingIds,
      EntityManager entityManager) {
    this.users = users;
    this.devices = devices;
    this.operations = operations;
    this.credentials = credentials;
    this.clock = clock;
    this.bindingIds = bindingIds;
    this.entityManager = entityManager;
  }

  @Transactional(readOnly = true)
  public PushDeviceStatusResult status(
      long userId, String installationId, String installationKey) {
    requireCanonicalUuid(installationId);
    PushDevice device =
        devices
            .findByInstallationId(installationId)
            .filter(
                found ->
                    credentials.matchesInstallationKey(
                        installationKey, found.getInstallationKeyDigest()))
            .orElseThrow(this::notFound);
    boolean owned = device.getUserId() != null && device.getUserId() == userId;
    return new PushDeviceStatusResult(
        device.getRevision(),
        owned,
        owned ? device.getBindingId() : null,
        device.getStatus(),
        device.getPlatform());
  }

  @Transactional
  public PushDeviceRegistrationResult register(
      long userId, String sessionId, RegisterPushDeviceCommand command) {
    validateRegistration(command);
    User user =
        users
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIAL));
    if (user.getStatus() != UserStatus.ACTIVE || user.getEmailVerifiedAt() == null) {
      throw new BusinessException(AuthErrorCode.INVALID_CREDENTIAL);
    }
    Instant now = clock.instant();
    Optional<PushDevice> currentView = devices.findByInstallationId(command.installationId());
    String keyDigest;
    try {
      keyDigest = credentials.digestInstallationKey(command.installationKey());
    } catch (IllegalArgumentException exception) {
      throw notFound();
    }
    if (currentView.isPresent()
        && !credentials.matchesInstallationKey(
            command.installationKey(), currentView.get().getInstallationKeyDigest())) {
      throw notFound();
    }
    validateOperationTime(command.operationIssuedAt(), now);

    String tokenDigest =
        command.permission() == PushPermission.GRANTED
            ? credentials.digestPushToken(command.pushToken())
            : null;
    Optional<PushDevice> tokenOwnerView =
        tokenDigest == null
            ? Optional.empty()
            : devices.findByProviderAndPushTokenDigest(command.provider(), tokenDigest);
    List<String> installationIdsToLock =
        java.util.stream.Stream.concat(
                currentView.stream().map(PushDevice::getInstallationId),
                tokenOwnerView.stream().map(PushDevice::getInstallationId))
            .distinct()
            .sorted()
            .toList();
    // A locking query must hydrate fresh entities, not reuse a prior consistent-read snapshot.
    // refresh after an existing lock can emit a non-locking SELECT on Hibernate/MySQL.
    java.util.stream.Stream.concat(currentView.stream(), tokenOwnerView.stream())
        .distinct().forEach(entityManager::detach);
    List<PushDevice> lockedDevices =
        installationIdsToLock.isEmpty()
            ? List.of()
            : devices.findAllByInstallationIdInForUpdate(installationIdsToLock);
    Optional<PushDevice> currentOptional =
        lockedDevices.stream()
            .filter(device -> device.getInstallationId().equals(command.installationId()))
            .findFirst();
    Optional<PushDevice> tokenOwner =
        tokenDigest == null
            ? Optional.empty()
            : lockedDevices.stream()
                .filter(
                    device ->
                        device.getProvider() == command.provider()
                            && tokenDigest.equals(device.getPushTokenDigest()))
                .findFirst();
    if (currentOptional.isPresent()
        && !credentials.matchesInstallationKey(
            command.installationKey(), currentOptional.get().getInstallationKeyDigest())) {
      throw notFound();
    }

    String requestDigest = requestDigest(command);
    if (currentOptional.isPresent()) {
      Optional<PushDeviceOperation> replay =
          operations.findByInstallationIdAndOperationId(
              command.installationId(), command.operationId());
      if (replay.isPresent()) {
        if (!replay.get().matchesRegistration(userId, requestDigest)) {
          throw new BusinessException(PushErrorCode.OPERATION_CONFLICT);
        }
        return replay.get().registrationResult();
      }
    }

    if (command.permission() == PushPermission.DENIED) {
      if (currentOptional.isEmpty()) {
        return new PushDeviceRegistrationResult(
            command.installationId(), 0L, null, PushDeviceStatus.DISABLED, userId);
      }
      PushDevice current = currentOptional.get();
      requireRevision(current, command.expectedRevision());
      if (current.disable(now)) {
        devices.save(current);
      }
      PushDeviceRegistrationResult result = registrationResult(current, userId);
      operations.save(
          PushDeviceOperation.successfulRegistration(
              command.installationId(),
              command.operationId(),
              userId,
              requestDigest,
              command.operationIssuedAt(),
              result,
              now));
      return result;
    }

    PushDevice current;
    if (currentOptional.isEmpty()) {
      if (command.expectedRevision() != 0L) {
        throw new BusinessException(PushErrorCode.REVISION_CONFLICT);
      }
      moveTokenIfAllowed(tokenOwner, null, userId, now);
      current =
          PushDevice.registered(
              command.installationId(),
              keyDigest,
              userId,
              sessionId,
              bindingIds.next().toString(),
              command.platform(),
              command.provider(),
              command.pushToken(),
              tokenDigest);
    } else {
      current = currentOptional.get();
      requireRevision(current, command.expectedRevision());
      moveTokenIfAllowed(tokenOwner, current, userId, now);
      boolean sameActiveUser =
          current.getStatus() == PushDeviceStatus.ACTIVE
              && current.getUserId() != null
              && current.getUserId() == userId;
      String bindingId = sameActiveUser ? current.getBindingId() : bindingIds.next().toString();
      current.register(
          userId,
          sessionId,
          bindingId,
          command.platform(),
          command.provider(),
          command.pushToken(),
          tokenDigest);
    }
    devices.save(current);
    PushDeviceRegistrationResult result = registrationResult(current, userId);
    operations.save(
        PushDeviceOperation.successfulRegistration(
            command.installationId(),
            command.operationId(),
            userId,
            requestDigest,
            command.operationIssuedAt(),
            result,
            now));
    return result;
  }

  @Transactional
  public PushDeviceRevokeResult revoke(RevokePushDeviceCommand command) {
    validateRevoke(command);
    PushDevice current =
        devices
            .findByInstallationIdForUpdate(command.installationId())
            .filter(
                found ->
                    credentials.matchesInstallationKey(
                        command.installationKey(), found.getInstallationKeyDigest()))
            .orElseThrow(this::notFound);
    Instant now = clock.instant();
    validateOperationTime(command.operationIssuedAt(), now);
    String requestDigest = requestDigest(command);
    Optional<PushDeviceOperation> replay =
        operations.findByInstallationIdAndOperationId(
            command.installationId(), command.operationId());
    if (replay.isPresent()) {
      if (!replay.get().matchesRevoke(requestDigest)) {
        throw new BusinessException(PushErrorCode.OPERATION_CONFLICT);
      }
      return replay.get().revokeResult();
    }

    boolean matchesBinding = command.bindingId().equals(current.getBindingId());
    if (matchesBinding) {
      requireRevision(current, command.expectedRevision());
      if (current.revoke(now)) {
        devices.save(current);
      }
    }
    PushDeviceRevokeResult result = new PushDeviceRevokeResult(matchesBinding);
    operations.save(
        PushDeviceOperation.successfulRevoke(
            command.installationId(),
            command.operationId(),
            current.getUserId(),
            requestDigest,
            command.operationIssuedAt(),
            result,
            now));
    return result;
  }

  public String requestDigest(RegisterPushDeviceCommand command) {
    String tokenDigest =
        command.pushToken() == null ? "-" : credentials.digestPushToken(command.pushToken());
    return credentials.digestRequest(
        String.join(
            "|",
            "REGISTER",
            command.installationId(),
            command.operationId(),
            command.operationIssuedAt().toString(),
            Long.toString(command.expectedRevision()),
            command.platform().name(),
            command.provider().name(),
            tokenDigest,
            command.permission().name()));
  }

  private String requestDigest(RevokePushDeviceCommand command) {
    return credentials.digestRequest(
        String.join(
            "|",
            "REVOKE",
            command.installationId(),
            command.operationId(),
            command.operationIssuedAt().toString(),
            command.bindingId(),
            Long.toString(command.expectedRevision())));
  }

  private void moveTokenIfAllowed(
      Optional<PushDevice> tokenOwner, PushDevice current, long userId, Instant now) {
    if (tokenOwner.isEmpty() || tokenOwner.get() == current) {
      return;
    }
    PushDevice previous = tokenOwner.get();
    if (previous.getUserId() == null || previous.getUserId() != userId) {
      throw new BusinessException(PushErrorCode.TOKEN_CONFLICT);
    }
    if (previous.revoke(now)) {
      devices.saveAndFlush(previous);
    }
  }

  private PushDeviceRegistrationResult registrationResult(PushDevice device, long userId) {
    return new PushDeviceRegistrationResult(
        device.getInstallationId(),
        device.getRevision(),
        device.getBindingId(),
        device.getStatus(),
        userId);
  }

  private void requireRevision(PushDevice device, long expectedRevision) {
    if (device.getRevision() != expectedRevision) {
      throw new BusinessException(PushErrorCode.REVISION_CONFLICT);
    }
  }

  private void validateRegistration(RegisterPushDeviceCommand command) {
    requireCanonicalUuid(command.installationId());
    requireCanonicalUuid(command.operationId());
    if (command.expectedRevision() < 0
        || command.operationIssuedAt() == null
        || command.platform() == null
        || command.provider() == null
        || command.permission() == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
    if (command.permission() == PushPermission.GRANTED
        && (command.pushToken() == null
            || command.pushToken().length() > 512
            || !EXPO_TOKEN.matcher(command.pushToken()).matches())) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
    if (command.permission() == PushPermission.DENIED && command.pushToken() != null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private void validateRevoke(RevokePushDeviceCommand command) {
    requireCanonicalUuid(command.installationId());
    requireCanonicalUuid(command.operationId());
    requireCanonicalUuid(command.bindingId());
    if (command.expectedRevision() < 0 || command.operationIssuedAt() == null) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private void requireCanonicalUuid(String input) {
    try {
      UUID parsed = UUID.fromString(input);
      if (input == null
          || input.length() != 36
          || !parsed.toString().equalsIgnoreCase(input)) {
        throw new IllegalArgumentException("non-canonical UUID");
      }
    } catch (RuntimeException exception) {
      throw new BusinessException(CommonErrorCode.INVALID_INPUT);
    }
  }

  private void validateOperationTime(Instant issuedAt, Instant now) {
    if (!issuedAt.isAfter(now.minus(OPERATION_LIFETIME))
        || issuedAt.isAfter(now.plus(MAX_FUTURE_SKEW))) {
      throw new BusinessException(PushErrorCode.OPERATION_EXPIRED);
    }
  }

  private BusinessException notFound() {
    return new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
  }
}
