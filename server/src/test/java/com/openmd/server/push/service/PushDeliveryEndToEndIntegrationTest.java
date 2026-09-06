package com.openmd.server.push.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.openmd.server.auth.domain.User;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.domain.SourceType;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.notification.domain.QuizGenerationNotification;
import com.openmd.server.notification.repository.NotificationRepository;
import com.openmd.server.push.domain.PushDevice;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.domain.PushProvider;
import com.openmd.server.push.dto.model.PushGatewayResult;
import com.openmd.server.push.dto.model.PushMessage;
import com.openmd.server.push.repository.PushDeviceRepository;
import com.openmd.server.quiz.domain.QuizGenerationCandidate;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.repository.QuizSetRepository;
import com.openmd.server.quiz.service.QuizGenerationPersistenceService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real Spring proxies + JPA/MySQL + worker; only the external provider is replaced. */
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
@Import(PushDeliveryEndToEndIntegrationTest.TimeConfiguration.class)
class PushDeliveryEndToEndIntegrationTest {
  @Container
  static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
      .withDatabaseName("push_worker_e2e").withUsername("test").withPassword("test")
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
  @Autowired QuizGenerationPersistenceService generation;
  @Autowired PushDeliveryWorker worker;
  @Autowired MutableClock clock;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean PushGateway gateway;

  @Test
  void actualOutboxIsCommittedBeforeHttpAndReceiptsNeverResendAfterTheSendWindow() {
    var set = fixture();
    var question = new QuizGenerationCandidate(QuestionType.SHORT_ANSWER, "주제", "질문?",
        "해설", "근거", List.of(), List.of("정답"), List.of(), "", List.of());
    generation.complete(set.getUserId(), set.getPublicId(), List.of(question), 1);
    var notification = notification(set);
    // The OS title must remain the original notification snapshot, not the renamed quiz.
    jdbc.update("UPDATE quiz_sets SET quiz_title='나중에 바뀐 제목' WHERE id=?", set.getId());
    clock.now = notification.getCreatedAt().plusSeconds(1);

    when(gateway.sendBatch(anyList())).thenAnswer(invocation -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      assertEquals("SENDING", state(notification)); // Visible through a separate JDBC connection.
      List<PushMessage> messages = invocation.getArgument(0);
      assertEquals(1, messages.size());
      var message = messages.getFirst();
      assertEquals("생성 당시 퀴즈 제목", message.title());
      assertEquals("퀴즈가 완성됐어요.", message.body());
      assertEquals(notification.getPublicId(), message.notificationId());
      assertEquals(notification.getCreatedAt().plusSeconds(3600), message.expiresAt());
      assertEquals(devices.findAllByUserId(set.getUserId()).getFirst().getBindingId(), message.bindingId());
      return List.of(PushGatewayResult.accepted("ticket-success"));
    });
    worker.sendDue();
    assertEquals("TICKET_ACCEPTED", state(notification));
    worker.sendDue();
    verify(gateway, times(1)).sendBatch(anyList());

    clock.now = notification.getCreatedAt().plusSeconds(14 * 60);
    worker.checkReceipts();
    verify(gateway, never()).getReceipts(anyList());

    // Already accepted tickets remain queryable after the one-hour *send* deadline.
    clock.now = notification.getCreatedAt().plusSeconds(3601);
    when(gateway.getReceipts(List.of("ticket-success"))).thenAnswer(invocation -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      assertEquals("RECEIPT_CHECKING", state(notification));
      return Map.of("ticket-success", PushGatewayResult.accepted(null));
    });
    worker.checkReceipts();
    assertEquals("PROVIDER_ACCEPTED", state(notification));
    worker.sendDue();
    verify(gateway, times(1)).sendBatch(anyList());
    assertNull(notifications.findById(notification.getId()).orElseThrow().getReadAt());
  }

  @Test
  void failedGenerationUsesFailureCopyAndTheSameAbsoluteExpiration() {
    var set = fixture();
    generation.failGeneration(set.getUserId(), set.getPublicId());
    var notification = notification(set);
    clock.now = notification.getCreatedAt().plusSeconds(1);
    when(gateway.sendBatch(anyList())).thenAnswer(invocation -> {
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      List<PushMessage> messages = invocation.getArgument(0);
      assertEquals(1, messages.size());
      assertEquals("퀴즈를 만들지 못했어요. 앱에서 확인해 주세요.", messages.getFirst().body());
      assertEquals(notification.getCreatedAt().plusSeconds(3600), messages.getFirst().expiresAt());
      return List.of(new PushGatewayResult(PushGatewayResult.Outcome.FAILED,
          null, "MESSAGE_TOO_BIG", Duration.ZERO));
    });
    worker.sendDue();
    assertEquals("FAILED", state(notification));
  }

  @Test
  void tokenRefreshAfterOutboxCreationCancelsTheOldSnapshotBeforeAnyHttp() {
    var set = fixture();
    generation.failGeneration(set.getUserId(), set.getPublicId());
    var notification = notification(set);
    var device = devices.findAllByUserId(set.getUserId()).getFirst();
    String originalBinding = device.getBindingId();
    device.register(set.getUserId(), device.getSessionId(), originalBinding,
        PushPlatform.IOS, PushProvider.EXPO, "ExponentPushToken[refreshed-token]", "b".repeat(64));
    devices.saveAndFlush(device);
    assertEquals(originalBinding, device.getBindingId());
    clock.now = notification.getCreatedAt().plusSeconds(1);

    worker.sendDue();

    assertEquals("CANCELLED", state(notification));
    verifyNoInteractions(gateway);
  }

  private QuizSet fixture() {
    String email = UUID.randomUUID() + "@example.com";
    var user = User.pending(email, email, "hash");
    user.activate(Instant.now());
    user = users.saveAndFlush(user);
    var material = materials.saveAndFlush(LearningMaterial.create(user.getId(), "자료", "내용",
        SourceType.PASTE, new byte[32], new byte[32]));
    String id = UUID.randomUUID().toString();
    devices.saveAndFlush(PushDevice.registered(id, "a".repeat(64), user.getId(), "session-" + id,
        UUID.randomUUID().toString(), PushPlatform.IOS, PushProvider.EXPO,
        "ExponentPushToken[" + id + "]", id.replace("-", "").repeat(2)));
    return sets.saveAndFlush(QuizSet.generating(user.getId(), material.getId(), "생성 당시 퀴즈 제목"));
  }

  private QuizGenerationNotification notification(QuizSet set) {
    return notifications.findAll().stream()
        .filter(n -> n.getQuizSetId().equals(set.getPublicId())).findFirst().orElseThrow();
  }

  private String state(QuizGenerationNotification notification) {
    return jdbc.queryForObject("SELECT state FROM push_deliveries WHERE notification_id=?",
        String.class, notification.getId());
  }

  static final class MutableClock extends Clock {
    volatile Instant now = Instant.now();
    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    @Override public Instant instant() { return now; }
  }

  @TestConfiguration
  static class TimeConfiguration {
    @Bean MutableClock workerTestClock() { return new MutableClock(); }
  }
}
