package com.openmd.server.push.integration.fcm;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class GoogleFcmAccessTokenSupplier implements FcmAccessTokenSupplier {
  private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
  private final GoogleCredentials credentials;

  public GoogleFcmAccessTokenSupplier(Path serviceAccountPath) throws IOException {
    try (InputStream input = Files.newInputStream(serviceAccountPath)) {
      credentials = GoogleCredentials.fromStream(input).createScoped(List.of(SCOPE));
    }
  }

  @Override
  public synchronized String accessToken() throws IOException {
    credentials.refreshIfExpired();
    if (credentials.getAccessToken() == null) {
      credentials.refresh();
    }
    return credentials.getAccessToken().getTokenValue();
  }
}
