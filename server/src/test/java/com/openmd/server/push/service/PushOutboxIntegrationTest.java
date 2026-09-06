package com.openmd.server.push.service;

import static org.junit.jupiter.api.Assertions.*;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.notification.domain.QuizGenerationNotification;
import com.openmd.server.notification.repository.NotificationRepository;
import com.openmd.server.notification.service.NotificationService;
import com.openmd.server.push.domain.PushDevice;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.repository.PushDeviceRepository;
import com.openmd.server.quiz.domain.QuizGenerationCandidate;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.repository.QuizSetRepository;
import com.openmd.server.quiz.service.QuizGenerationPersistenceService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
    "openmd.auth.enabled=false", "openmd.quiz.generation.enabled=false",
    "openmd.push.delivery-enabled=true", "openmd.push.scheduler-enabled=false",
    "spring.jpa.open-in-view=false",
    "spring.autoconfigure.exclude=org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
        + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
        + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
})
class PushOutboxIntegrationTest {
  @Container
  static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
      .withDatabaseName("push_outbox").withUsername("test").withPassword("test")
      .withStartupTimeout(Duration.ofMinutes(2));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
  }

  @Autowired UserRepository users;
  @Autowired LearningMaterialRepository materials;
  @Autowired QuizSetRepository sets;
  @Autowired PushDeviceRepository devices;
  @Autowired NotificationRepository notifications;
  @Autowired NotificationService notificationService;
  @Autowired QuizGenerationPersistenceService generation;
  @Autowired PushOutboxService outbox;
  @Autowired JdbcTemplate jdbc;

  @Test
  void successfulGenerationCreatesOneNotificationAndTwoDeliveriesAndCannotDuplicate() {
    var set = fixture(2);
    var question = new QuizGenerationCandidate(QuestionType.SHORT_ANSWER, "주제", "질문?",
        "해설", "근거", List.of(), List.of("정답"), List.of(), "", List.of());
    assertEquals(1, generation.complete(set.getUserId(), set.getPublicId(), List.of(question), 1));
    assertEquals(1, count("notifications", "quiz_set_id", set.getPublicId()));
    assertEquals(2, count("push_deliveries", "user_id", set.getUserId()));
    assertEquals(0, generation.complete(set.getUserId(), set.getPublicId(), List.of(question), 1));
    assertEquals(2, count("push_deliveries", "user_id", set.getUserId()));
    assertEquals(0, jdbc.queryForObject("""
        SELECT COUNT(*) FROM push_deliveries d JOIN notifications n ON n.id=d.notification_id
        WHERE d.user_id=? AND TIMESTAMPDIFF(SECOND,n.created_at,d.expires_at) <> 3600
        """, Integer.class, set.getUserId()));
  }

  @Test
  void outboxInsertFailureRollsBackQuizStatusAndNotificationThenCanRetry() {
    var set = fixture(2);
    jdbc.execute("ALTER TABLE push_deliveries ADD CONSTRAINT test_push_insert_failure CHECK (user_id <> "
        + set.getUserId() + ")");
    try {
      assertThrows(DataIntegrityViolationException.class,
          () -> generation.failGeneration(set.getUserId(), set.getPublicId()));
      assertEquals(QuizSetStatus.GENERATING,
          sets.findById(set.getId()).orElseThrow().getStatus());
      assertEquals(0, count("notifications", "quiz_set_id", set.getPublicId()));
      assertEquals(0, count("push_deliveries", "user_id", set.getUserId()));
    } finally {
      jdbc.execute("ALTER TABLE push_deliveries DROP CHECK test_push_insert_failure");
    }
    generation.failGeneration(set.getUserId(), set.getPublicId());
    assertEquals(2, count("push_deliveries", "user_id", set.getUserId()));
  }

  @Test
  void sourceInsufficientAndStartupAndStaleRecoveryAlsoCreateDeliveries() {
    var insufficient = fixture(1);
    generation.complete(insufficient.getUserId(), insufficient.getPublicId(), List.of(), 5);
    assertEquals(1, count("push_deliveries", "user_id", insufficient.getUserId()));

    var interrupted = fixture(1);
    generation.failInterruptedGenerations(Instant.now().plusSeconds(10));
    assertEquals(1, count("push_deliveries", "user_id", interrupted.getUserId()));

    var stale = fixture(1);
    generation.markStarted(stale.getUserId(), stale.getPublicId());
    generation.failStaleGenerations(Instant.now().plusSeconds(10));
    assertEquals(1, count("push_deliveries", "user_id", stale.getUserId()));
  }

  @Test
  void noDeviceStillStoresResultAndLateDeviceDoesNotBackfill() {
    var set = fixture(0);
    generation.failGeneration(set.getUserId(), set.getPublicId());
    device(set.getUserId());
    generation.failGeneration(set.getUserId(), set.getPublicId());
    assertEquals(1, count("notifications", "quiz_set_id", set.getPublicId()));
    assertEquals(0, count("push_deliveries", "user_id", set.getUserId()));
  }

  @Test
  void enqueueCannotRunWithoutTheResultTransaction() {
    assertThrows(IllegalTransactionStateException.class,
        () -> outbox.enqueue(QuizGenerationNotification.from(QuizSet.ready(1L, 1L, "제목"))));
  }

  @Test
  void singleLookupHonorsOwnershipAndRetentionWithoutMarkingRead() {
    var set = fixture(0);
    generation.failGeneration(set.getUserId(), set.getPublicId());
    var n = notifications.findAll().stream()
        .filter(value -> value.getQuizSetId().equals(set.getPublicId())).findFirst().orElseThrow();
    assertNull(notificationService.get(set.getUserId(), n.getPublicId()).readAt());
    assertThrows(com.openmd.server.global.error.BusinessException.class,
        () -> notificationService.get(set.getUserId() + 999999, n.getPublicId()));
    jdbc.update("UPDATE notifications SET created_at = TIMESTAMPADD(DAY,-91,NOW(6)) WHERE id=?", n.getId());
    assertThrows(com.openmd.server.global.error.BusinessException.class,
        () -> notificationService.get(set.getUserId(), n.getPublicId()));
  }

  private QuizSet fixture(int deviceCount) {
    String email = UUID.randomUUID() + "@example.com";
    var user = User.pending(email, email, "hash");
    user.activate(Instant.now());
    user = users.saveAndFlush(user);
    var material = materials.saveAndFlush(LearningMaterial.create(user.getId(), "자료", "내용",
        SourceType.PASTE, new byte[32], new byte[32]));
    for (int i = 0; i < deviceCount; i++) device(user.getId());
    return sets.saveAndFlush(QuizSet.generating(user.getId(), material.getId(), "푸시 퀴즈"));
  }

  private void device(long userId) {
    String id = UUID.randomUUID().toString();
    devices.saveAndFlush(PushDevice.registered(id, "a".repeat(64), userId, "session-" + id,
        UUID.randomUUID().toString(), PushPlatform.IOS, PushProvider.EXPO,
        "ExponentPushToken[" + id + "]", id.replace("-", "").repeat(2)));
  }

  private int count(String table, String column, Object value) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?",
        Integer.class, value);
  }
}
