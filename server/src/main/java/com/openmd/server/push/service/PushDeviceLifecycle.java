package com.openmd.server.push.service;

public interface PushDeviceLifecycle {

  void revokeSession(String sessionId);

  void deleteForUser(long userId);
}
