package com.openmd.server.quiz.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class QuizGenerationWorkerMigrationTest {

  @Container
  static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
      .withDatabaseName("openmd_generation_migration")
      .withUsername("openmd")
      .withPassword("openmd")
      .withStartupTimeout(Duration.ofMinutes(2));

  @Test
  void failsOlderDuplicateGenerationsBeforeAddingTheUserUniqueConstraint() {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .target(MigrationVersion.fromVersion("9"))
        .load()
        .migrate();
    JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    jdbc.update("""
        INSERT INTO users (
          id, email, normalized_email, password_hash, email_verified_at, status,
          activated_at, created_at, updated_at
        ) VALUES (1, 'legacy@example.com', 'legacy@example.com', 'hash', NOW(6),
          'ACTIVE', NOW(6), NOW(6), NOW(6))
        """);
    for (int id = 1; id <= 2; id++) {
      jdbc.update("""
          INSERT INTO learning_materials (
            id, user_id, title, content, source_type,
            idempotency_key_hash, request_fingerprint, created_at, updated_at
          ) VALUES (?, 1, '자료', '내용', 'PASTE',
            UNHEX(SHA2(CONCAT('key', ?), 256)), UNHEX(SHA2(CONCAT('body', ?), 256)),
            NOW(6), NOW(6))
          """, id, id, id);
      jdbc.update("""
          INSERT INTO quiz_sets (
            id, public_id, user_id, learning_material_id, quiz_title, status,
            failure_code, created_at, updated_at
          ) VALUES (?, ?, 1, ?, '자료 퀴즈', 'GENERATING', NULL,
            TIMESTAMPADD(SECOND, ?, NOW(6)), TIMESTAMPADD(SECOND, ?, NOW(6)))
          """, id, "00000000-0000-0000-0000-00000000000" + id, id, id, id);
    }

    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .target(MigrationVersion.fromVersion("10"))
        .load()
        .migrate();

    assertEquals("FAILED", jdbc.queryForObject(
        "SELECT status FROM quiz_sets WHERE id = 1", String.class));
    assertEquals("GENERATION_FAILED", jdbc.queryForObject(
        "SELECT failure_code FROM quiz_sets WHERE id = 1", String.class));
    assertEquals(1, jdbc.queryForObject(
        "SELECT COUNT(*) FROM notifications WHERE quiz_set_id = ?",
        Integer.class,
        "00000000-0000-0000-0000-000000000001"));
    assertEquals("GENERATING", jdbc.queryForObject(
        "SELECT status FROM quiz_sets WHERE id = 2", String.class));
    assertThrows(DataAccessException.class, () -> jdbc.update("""
        INSERT INTO quiz_sets (
          public_id, user_id, learning_material_id, quiz_title, status,
          failure_code, created_at, updated_at
        ) VALUES ('00000000-0000-0000-0000-000000000003', 1, 1, '새 퀴즈',
          'GENERATING', NULL, NOW(6), NOW(6))
        """));
  }
}
