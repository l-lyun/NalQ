package com.openmd.server.notion.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
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
class NotionConnectionMigrationTest {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
		.withDatabaseName("openmd_notion_migration")
		.withUsername("openmd")
		.withPassword("openmd")
		.withStartupTimeout(Duration.ofMinutes(2));

	private static JdbcTemplate jdbc;

	@BeforeAll
	static void migrate() {
		Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();
		jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
		jdbc.update("""
			INSERT INTO users (id, email, normalized_email, password_hash, email_verified_at, status,
			 activated_at, suspended_at, withdrawn_at, created_at, updated_at)
			VALUES (1, 'notion@example.com', 'notion@example.com', 'hash', NOW(6), 'ACTIVE',
			 NOW(6), NULL, NULL, NOW(6), NOW(6))
			""");
		jdbc.update("""
			INSERT INTO users (id, email, normalized_email, password_hash, email_verified_at, status,
			 activated_at, suspended_at, withdrawn_at, created_at, updated_at)
			VALUES (2, 'invalid-notion@example.com', 'invalid-notion@example.com', 'hash', NOW(6), 'ACTIVE',
			 NOW(6), NULL, NULL, NOW(6), NOW(6))
			""");
	}

	@Test
	void createsOneConnectionPerUserAndRestrictsStatus() {
		insert(1, "CONNECTED");
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notion_connections", Integer.class));
		assertThrows(DataAccessException.class, () -> insert(2, "CONNECTED"));
		assertThrows(DataAccessException.class, () -> jdbc.update("""
			INSERT INTO notion_connections (user_id, workspace_id, bot_id, access_token_ciphertext,
			 refresh_token_ciphertext, encryption_key_version, status, created_at, updated_at)
			VALUES (2, 'workspace-2', 'bot-2', X'01', X'02', 'v1', 'UNKNOWN', NOW(6), NOW(6))
			"""));
	}

	private static void insert(long suffix, String status) {
		jdbc.update("""
			INSERT INTO notion_connections (user_id, workspace_id, bot_id, access_token_ciphertext,
			 refresh_token_ciphertext, encryption_key_version, status, created_at, updated_at)
			VALUES (1, ?, ?, X'01', X'02', 'v1', ?, NOW(6), NOW(6))
			""", "workspace-" + suffix, "bot-" + suffix, status);
	}
}
