package com.openmd.server.push.error;

import com.openmd.server.global.error.BusinessException;

public final class PushRateLimitedException extends BusinessException {

  private final long retryAfterSeconds;

  public PushRateLimitedException(long retryAfterSeconds) {
    super(PushErrorCode.RATE_LIMITED);
    this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
