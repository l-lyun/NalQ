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
class HomeLearningMigrationTest {
  @Container
  static final MySQLContainer MYSQL =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("openmd_home_learning_migration")
          .withUsername("openmd")
          .withPassword("openmd")
          .withStartupTimeout(Duration.ofMinutes(2));

  @Test
  void backfillsQuizTitlesAndEnforcesOneVisitPerUserAndDate() {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .target(MigrationVersion.fromVersion("6"))
        .load()
        .migrate();
    JdbcTemplate jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    jdbc.update(
        """
        INSERT INTO users (
          id, email, normalized_email, password_hash, email_verified_at, status,
          activated_at, suspended_at, withdrawn_at, created_at, updated_at
        ) VALUES (1, 'home@example.com', 'home@example.com', 'hash', NOW(6), 'ACTIVE',
          NOW(6), NULL, NULL, NOW(6), NOW(6))
        """);
    jdbc.update(
        """
        INSERT INTO learning_materials (
          id, user_id, title, content, source_type, idempotency_key_hash,
          request_fingerprint, created_at, updated_at
        ) VALUES (1, 1, ?, '내용', 'PASTE', UNHEX(REPEAT('01', 32)),
          UNHEX(REPEAT('02', 32)), NOW(6), NOW(6))
        """,
        "😀".repeat(255));
    jdbc.update(
        """
        INSERT INTO quiz_sets (
          id, public_id, user_id, learning_material_id, status, failure_code, created_at, updated_at
        ) VALUES (1, '00000000-0000-0000-0000-000000000001', 1, 1, 'GENERATING', NULL,
          NOW(6), NOW(6))
        """);

    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .load()
        .migrate();

    String title = jdbc.queryForObject("SELECT quiz_title FROM quiz_sets WHERE id=1", String.class);
    assertEquals(255, title.codePointCount(0, title.length()));
    assertEquals("😀".repeat(252) + " 퀴즈", title);
    jdbc.update(
        "INSERT INTO home_visits (user_id, visit_date, created_at, updated_at)"
            + " VALUES (1, '2026-08-28', NOW(6), NOW(6))");
    assertThrows(
        DataAccessException.class,
        () ->
            jdbc.update(
                "INSERT INTO home_visits (user_id, visit_date, created_at, updated_at)"
                    + " VALUES (1, '2026-08-28', NOW(6), NOW(6))"));
  }
}
