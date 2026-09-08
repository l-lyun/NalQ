package com.openmd.server.push.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class PushInstallationCredential {

  private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
  private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

  public String digestInstallationKey(String key) {
    byte[] decoded;
    try {
      decoded = BASE64_URL_DECODER.decode(key);
    } catch (RuntimeException exception) {
      throw new IllegalArgumentException("Installation key must be base64url", exception);
    }
    if (decoded.length != 32 || !BASE64_URL_ENCODER.encodeToString(decoded).equals(key)) {
      throw new IllegalArgumentException("Installation key must encode exactly 32 bytes");
    }
    return sha256Hex(decoded);
  }

  public boolean matchesInstallationKey(String key, String expectedDigest) {
    try {
      byte[] actual = digestInstallationKey(key).getBytes(StandardCharsets.US_ASCII);
      byte[] expected = expectedDigest.getBytes(StandardCharsets.US_ASCII);
      return MessageDigest.isEqual(actual, expected);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  public String digestPushToken(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Push token must not be blank");
    }
    return sha256Hex(token.getBytes(StandardCharsets.UTF_8));
  }

  public String digestRequest(String canonicalRequest) {
    return sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
  }

  private String sha256Hex(byte[] input) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }
}
