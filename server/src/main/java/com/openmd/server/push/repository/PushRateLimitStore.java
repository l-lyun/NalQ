package com.openmd.server.push.repository;

public interface PushRateLimitStore {

  /** Returns zero when allowed, otherwise the retry delay in whole seconds. */
  long consume(String scope, String subject, int limit, int windowSeconds);
}
