package com.openmd.server.push.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PushInstallationCredentialTest {

  private final PushInstallationCredential credential = new PushInstallationCredential();

  @Test
  void storesOnlyADigestAndVerifiesTheOriginalThirtyTwoByteKey() {
    String key = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    String digest = credential.digestInstallationKey(key);

    assertFalse(digest.contains(key));
    assertTrue(credential.matchesInstallationKey(key, digest));
    assertFalse(
        credential.matchesInstallationKey(
            "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", digest));
  }

  @Test
  void rejectsKeysThatAreNotCanonicalUnpaddedBase64UrlOfThirtyTwoBytes() {
    assertThrows(
        IllegalArgumentException.class,
        () -> credential.digestInstallationKey("short"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            credential.digestInstallationKey(
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
  }
}
