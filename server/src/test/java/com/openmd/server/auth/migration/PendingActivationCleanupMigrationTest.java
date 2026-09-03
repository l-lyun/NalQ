package com.openmd.server.auth.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
class PendingActivationCleanupMigrationTest {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
		.withDatabaseName("openmd_migration")
		.withUsername("openmd")
		.withPassword("openmd")
		.withStartupTimeout(Duration.ofMinutes(2));

	@Test
	void migrationsPreserveLegacyRowsAndBackfillSuspendedAccountsBeforeTheWithdrawalConstraint() {
		Flyway.configure()
			.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
			.target(MigrationVersion.fromVersion("3"))
			.load()
			.migrate();
		JdbcTemplate jdbc = jdbc();

		insertPending(jdbc, 101L, "retry@example.com");
		insertActive(jdbc, 102L, "active@example.com");
		insertPending(jdbc, 103L, "referenced@example.com");
		insertNonPending(jdbc, 104L, "suspended@example.com", "SUSPENDED");
		insertNonPending(jdbc, 105L, "withdrawn@example.com", "WITHDRAWN");
		jdbc.update("""
			INSERT INTO learning_materials (
			  user_id, title, content, source_type, content_edit_status,
			  idempotency_key_hash, request_fingerprint, created_at, updated_at
			) VALUES (?, 'legacy', 'content', 'PASTE', 'EDITABLE',
			  UNHEX(SHA2('idempotency', 256)), UNHEX(SHA2('fingerprint', 256)),
			  CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			""", 103L);

		Flyway.configure()
			.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
			.target(MigrationVersion.fromVersion("4"))
			.load()
			.migrate();

		assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM users WHERE status = 'PENDING_ACTIVATION'"));
		assertEquals(0, count(jdbc, "SELECT COUNT(*) FROM users WHERE id = 101"));
		assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM users WHERE id = 102 AND status = 'ACTIVE'"));
		assertEquals("active@example.com", jdbc.queryForObject(
			"SELECT normalized_email FROM users WHERE id = 102", String.class
		));
		assertEquals("suspended@example.com", jdbc.queryForObject(
			"SELECT normalized_email FROM users WHERE id = 104 AND status = 'SUSPENDED'", String.class
		));
		assertEquals("withdrawn@example.com", jdbc.queryForObject(
			"SELECT normalized_email FROM users WHERE id = 105 AND status = 'WITHDRAWN'", String.class
		));
		assertEquals(1, count(jdbc, "SELECT COUNT(*) FROM learning_materials WHERE user_id = 103"));
		assertEquals("WITHDRAWN", jdbc.queryForObject("SELECT status FROM users WHERE id = 103", String.class));
		assertNotEquals("referenced@example.com", jdbc.queryForObject(
			"SELECT normalized_email FROM users WHERE id = 103", String.class
		));
		assertEquals(1, jdbc.update("""
			INSERT INTO users (
			  email, normalized_email, password_hash, status, created_at, updated_at
			) VALUES ('referenced@example.com', 'referenced@example.com', 'hash',
			  'PENDING_ACTIVATION', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			"""));

		Flyway.configure()
			.dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
			.target(MigrationVersion.fromVersion("11"))
			.load()
			.migrate();

		assertEquals(1, count(jdbc, """
			SELECT COUNT(*) FROM users
			WHERE id = 104 AND status = 'SUSPENDED'
			  AND email_verified_at IS NOT NULL AND activated_at IS NOT NULL
			"""));
		assertEquals("varchar", jdbc.queryForObject("""
			SELECT DATA_TYPE FROM information_schema.COLUMNS
			WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users'
			  AND COLUMN_NAME = 'withdrawal_request_id'
			""", String.class));
	}

	private JdbcTemplate jdbc() {
		return new JdbcTemplate(new DriverManagerDataSource(
			MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()
		));
	}

	private void insertPending(JdbcTemplate jdbc, long id, String email) {
		jdbc.update("""
			INSERT INTO users (
			  id, email, normalized_email, password_hash, status, created_at, updated_at
			) VALUES (?, ?, ?, 'hash', 'PENDING_ACTIVATION', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			""", id, email, email);
	}

	private void insertActive(JdbcTemplate jdbc, long id, String email) {
		jdbc.update("""
			INSERT INTO users (
			  id, email, normalized_email, password_hash, email_verified_at,
			  status, activated_at, created_at, updated_at
			) VALUES (?, ?, ?, 'hash', CURRENT_TIMESTAMP(6), 'ACTIVE', CURRENT_TIMESTAMP(6),
			  CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			""", id, email, email);
	}

	private void insertNonPending(JdbcTemplate jdbc, long id, String email, String status) {
		jdbc.update("""
			INSERT INTO users (
			  id, email, normalized_email, password_hash, status, created_at, updated_at
			) VALUES (?, ?, ?, 'hash', ?, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
			""", id, email, email, status);
	}

	private int count(JdbcTemplate jdbc, String sql) {
		return jdbc.queryForObject(sql, Integer.class);
	}
}
