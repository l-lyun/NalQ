package com.openmd.server.character.repository;

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
class CharacterShopMigrationTest {

  @Container
  static final MySQLContainer MYSQL =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("openmd_character_shop")
          .withUsername("openmd")
          .withPassword("openmd")
          .withStartupTimeout(Duration.ofMinutes(2));

  private static JdbcTemplate jdbc;

  @BeforeAll
  static void migrate() {
    Flyway.configure()
        .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
        .load()
        .migrate();
    jdbc =
        new JdbcTemplate(
            new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    jdbc.update(
        """
        INSERT INTO users (
          id, email, normalized_email, password_hash, nickname, email_verified_at, status,
          activated_at, created_at, updated_at
        ) VALUES (7, 'shop@example.com', 'shop@example.com', 'hash', 'shop7', NOW(6),
          'ACTIVE', NOW(6), NOW(6), NOW(6))
        """);
  }

  @Test
  void enforcesNonNegativeBalanceAndOneRewardPerUserAndQuizSet() {
    jdbc.update(
        """
        INSERT INTO character_profiles (
          user_id, coin_balance, character_item_id, room_item_id, hat_item_id, top_item_id,
          created_at, updated_at
        ) VALUES (7, 10, 'character-dragon', 'room-day', 'hat-none', 'top-none', NOW(6), NOW(6))
        """);
    assertThrows(
        DataAccessException.class,
        () -> jdbc.update("UPDATE character_profiles SET coin_balance=-1 WHERE user_id=7"));

    insertReward("00000000-0000-0000-0000-000000000001");
    assertThrows(
        DataAccessException.class,
        () -> insertReward("00000000-0000-0000-0000-000000000002"));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM quiz_coin_rewards WHERE user_id=7 AND quiz_set_id=11",
            Integer.class));
  }

  private static void insertReward(String attemptId) {
    jdbc.update(
        """
        INSERT INTO quiz_coin_rewards (
          user_id, quiz_set_id, attempt_public_id, amount, created_at, updated_at
        ) VALUES (7, 11, ?, 10, NOW(6), NOW(6))
        """,
        attemptId);
  }
}
