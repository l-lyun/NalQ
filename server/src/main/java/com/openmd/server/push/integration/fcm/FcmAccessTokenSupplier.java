package com.openmd.server.push.integration.fcm;

@FunctionalInterface
public interface FcmAccessTokenSupplier {
  String accessToken() throws Exception;
}
