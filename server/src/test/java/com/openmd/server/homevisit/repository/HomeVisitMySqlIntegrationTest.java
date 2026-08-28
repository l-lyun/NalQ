package com.openmd.server.homevisit.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.openmd.server.homevisit.service.HomeVisitService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Tag("integration")
@SpringBootTest(
    properties = {
      "openmd.auth.enabled=false",
      "spring.jpa.open-in-view=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
    })
class HomeVisitMySqlIntegrationTest {
  @Container
  static final MySQLContainer MYSQL =
      new MySQLContainer(DockerImageName.parse("mysql:8.4"))
          .withDatabaseName("openmd_home_visits")
          .withUsername("openmd")
          .withPassword("openmd")
          .withStartupTimeout(Duration.ofMinutes(2));

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired HomeVisitService service;

  @BeforeEach
  void setUp() {
    jdbc.update("DELETE FROM home_visits");
    jdbc.update("DELETE FROM users");
    jdbc.update(
        """
        INSERT INTO users (
          id, email, normalized_email, password_hash, email_verified_at, status,
          activated_at, suspended_at, withdrawn_at, created_at, updated_at
        ) VALUES (1, 'visit@example.com', 'visit@example.com', 'hash', NOW(6), 'ACTIVE',
          NOW(6), NULL, NULL, NOW(6), NOW(6))
        """);
  }

  @Test
  void repeatedCallsUseOneDailyRowAndReturnTheSameStreak() {
    var first = service.visit(1L);
    var retry = service.visit(1L);

    assertEquals(first, retry);
    assertEquals(1, first.consecutiveVisitDays());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM home_visits WHERE user_id=1 AND visit_date=?",
            Integer.class,
            first.visitDate()));
  }
}
