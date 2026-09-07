package com.openmd.server.push.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.push.dto.command.RegisterPushDeviceCommand;
import com.openmd.server.push.dto.command.RevokePushDeviceCommand;
import com.openmd.server.push.dto.response.PushDeviceRegistrationResult;
import com.openmd.server.push.dto.response.PushDeviceRevokeResult;
import com.openmd.server.push.dto.response.PushDeviceStatusResult;
import com.openmd.server.push.error.PushErrorCode;
import com.openmd.server.push.error.PushRateLimitedException;
import com.openmd.server.push.repository.PushRateLimitStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "openmd.push.registration-enabled", havingValue = "true")
public class PushDeviceService {

  private static final int ACCOUNT_LIMIT = 60;
  private static final int INSTALLATION_LIMIT = 20;
  private static final int WINDOW_SECONDS = 60;

  private final PushDeviceTransaction transaction;
  private final PushRateLimitStore rateLimits;

  public PushDeviceService(PushDeviceTransaction transaction, PushRateLimitStore rateLimits) {
    this.transaction = transaction;
    this.rateLimits = rateLimits;
  }

  public PushDeviceStatusResult status(
      long userId, String installationId, String installationKey) {
    return transaction.status(userId, installationId, installationKey);
  }

  public PushDeviceRegistrationResult register(
      long userId, String sessionId, RegisterPushDeviceCommand command) {
    checkLimit("account", Long.toString(userId), ACCOUNT_LIMIT);
    checkLimit("installation", command.installationId(), INSTALLATION_LIMIT);
    try {
      return transaction.register(userId, sessionId, command);
    } catch (DataIntegrityViolationException | TransientDataAccessException firstFailure) {
      try {
        return transaction.register(userId, sessionId, command);
      } catch (DataAccessException secondFailure) {
        throw new BusinessException(PushErrorCode.DEPENDENCY_UNAVAILABLE);
      }
    }
  }

  public PushDeviceRevokeResult revoke(RevokePushDeviceCommand command) {
    checkLimit("installation", command.installationId(), INSTALLATION_LIMIT);
    try {
      return transaction.revoke(command);
    } catch (TransientDataAccessException firstFailure) {
      try {
        return transaction.revoke(command);
      } catch (DataAccessException secondFailure) {
        throw new BusinessException(PushErrorCode.DEPENDENCY_UNAVAILABLE);
      }
    }
  }

  private void checkLimit(String scope, String subject, int limit) {
    long retryAfter;
    try {
      retryAfter = rateLimits.consume(scope, subject, limit, WINDOW_SECONDS);
    } catch (RuntimeException exception) {
      throw new BusinessException(PushErrorCode.DEPENDENCY_UNAVAILABLE);
    }
    if (retryAfter > 0) {
      throw new PushRateLimitedException(retryAfter);
    }
  }
}
