package com.openmd.server.learningmaterial.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class LearningMaterialContentRevision {
  private static final Pattern LOWERCASE_SHA256 = Pattern.compile("[0-9a-f]{64}");

  private LearningMaterialContentRevision() {}

  public static String from(String content) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public static boolean isValid(String revision) {
    return revision != null && LOWERCASE_SHA256.matcher(revision).matches();
  }
}
