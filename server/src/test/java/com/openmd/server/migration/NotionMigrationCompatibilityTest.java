package com.openmd.server.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class NotionMigrationCompatibilityTest {

  @Test
  void keepsAppliedV8ImmutableAndMovesPendingRevocationColumnsToV12() throws IOException {
    String v8 = migration("db/migration/V8__create_notion_connections.sql");
    String v12 = migration("db/migration/V12__add_notion_pending_revocation.sql");

    assertFalse(v8.contains("pending_revocation"));
    assertTrue(v12.contains("ALTER TABLE notion_connections"));
    assertTrue(v12.contains("pending_revocation_workspace_id"));
    assertTrue(v12.contains("chk_notion_connections_pending_revocation"));
  }

  private String migration(String path) throws IOException {
    try (InputStream stream = getClass().getClassLoader().getResourceAsStream(path)) {
      assertNotNull(stream, () -> "Missing migration: " + path);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
