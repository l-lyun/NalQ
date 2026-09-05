package com.openmd.server.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class NotionMigrationCompatibilityTest {

  @Test
  void keepsAppliedV8ByteForByteAndDoesNotReplayItsColumns() throws IOException {
    byte[] v8 = migration("db/migration/V8__create_notion_connections.sql");

    assertEquals(
        "0412293b6cfadbb7caf63566b74e8e81599dd1be47a662a30bd4c60d35d41881", sha256(v8));
    assertNull(
        getClass()
            .getClassLoader()
            .getResource("db/migration/V12__add_notion_pending_revocation.sql"));
  }

  private byte[] migration(String path) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
      assertNotNull(stream, () -> "Missing migration: " + path);
      return stream.readAllBytes();
    }
  }

  private String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
    }
  }
}
