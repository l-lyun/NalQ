package com.openmd.server.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.notification.domain.QuizGenerationNotification;
import com.openmd.server.notification.repository.NotificationRepository;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizSetFailureCode;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationServiceTest {
  private static final Instant NOW = Instant.parse("2026-09-03T02:00:00Z");
  private static final Instant RETAINED_SINCE = NOW.minusSeconds(90L * 24 * 60 * 60);
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final QuizSetRepository quizSets = mock(QuizSetRepository.class);
  private final LearningMaterialRepository materials = mock(LearningMaterialRepository.class);
  private final NotificationService service =
      new NotificationService(
          notifications, quizSets, materials, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void retrievesOneOwnedNotificationWithoutReadingItAndKeepsDeletedTargetSnapshot() {
    var notification = notification("00000000-0000-4000-8000-000000000020", 20L);
    when(notifications.findOwnedRetained(notification.getPublicId(), 7L, RETAINED_SINCE))
        .thenReturn(Optional.of(notification));

    var item = service.get(7L, notification.getPublicId());

    assertEquals(notification.getPublicId(), item.notificationId());
    assertEquals("퀴즈 20", item.targetName());
    assertFalse(item.targetAvailable());
    assertNull(item.readAt());
    assertNull(notification.getReadAt());
  }

  @Test
  void singleLookupRejectsForeignMissingAndExpiredNotificationsUsingOwnedRetentionQuery() {
    String id = "00000000-0000-4000-8000-000000000020";
    when(notifications.findOwnedRetained(id, 7L, RETAINED_SINCE)).thenReturn(Optional.empty());

    var error = assertThrows(BusinessException.class, () -> service.get(7L, id));

    assertEquals(CommonErrorCode.RESOURCE_NOT_FOUND, error.getErrorCode());
    org.mockito.Mockito.verify(notifications).findOwnedRetained(id, 7L, RETAINED_SINCE);
    org.mockito.Mockito.verifyNoInteractions(quizSets, materials);
  }

  @Test
  void listsAtMostTwentyRetainedNotificationsInStableOrderAndCountsAllUnread() {
    QuizGenerationNotification recent = notification("00000000-0000-0000-0000-000000000020", 20L);
    QuizGenerationNotification older = notification("00000000-0000-0000-0000-000000000019", 19L);
    QuizGenerationNotification boundary = notification("00000000-0000-0000-0000-000000000018", 18L);
    when(notifications.findPage(eq(7L), eq(RETAINED_SINCE), any(), any(), any(Pageable.class)))
        .thenReturn(List.of(recent, older, boundary));
    when(notifications.countUnread(7L, RETAINED_SINCE)).thenReturn(23L);
    when(quizSets.findAllByPublicIdInAndUserId(any(), eq(7L))).thenReturn(List.of());
    when(materials.findAllByIdInAndUserId(any(), eq(7L))).thenReturn(List.of());

    var result = service.list(7L, null, 2);

    assertEquals(2, result.items().size());
    assertEquals(23L, result.unreadCount());
    assertTrue(result.hasNext());
    assertTrue(result.nextCursor() != null && !result.nextCursor().isBlank());
    assertEquals(recent.getPublicId(), result.items().getFirst().notificationId());
  }

  @Test
  void readingAgainKeepsTheFirstReadTimeAndReturnsCurrentUnreadCount() {
    QuizGenerationNotification notification = notification("00000000-0000-0000-0000-000000000020", 20L);
    Instant firstReadAt = Instant.parse("2026-09-03T01:00:00Z");
    notification.markRead(firstReadAt);
    when(notifications.findOwnedRetained(notification.getPublicId(), 7L, RETAINED_SINCE))
        .thenReturn(Optional.of(notification));
    when(notifications.countUnread(7L, RETAINED_SINCE)).thenReturn(2L);

    var result = service.read(7L, notification.getPublicId());

    assertEquals(firstReadAt, result.readAt());
    assertEquals(2L, result.unreadCount());
  }

  @Test
  void readAllOnlyUpdatesUnreadNotificationsAtOrBeforeTheVisibleBoundary() {
    QuizGenerationNotification boundary = notification("00000000-0000-0000-0000-000000000020", 20L);
    when(notifications.findOwnedRetained(boundary.getPublicId(), 7L, RETAINED_SINCE))
        .thenReturn(Optional.of(boundary));
    when(notifications.markUnreadThrough(
            7L, RETAINED_SINCE, boundary.getCreatedAt(), boundary.getPublicId(), NOW))
        .thenReturn(4);
    when(notifications.countUnread(7L, RETAINED_SINCE)).thenReturn(1L);

    var result = service.readAll(7L, boundary.getPublicId());

    assertEquals(NOW, result.readAt());
    assertEquals(4, result.updatedCount());
    assertEquals(1L, result.unreadCount());
  }

  @Test
  void projectsAvailabilityFromCurrentOwnedTargetsWithoutChangingSnapshots() {
    QuizGenerationNotification ready = notification("00000000-0000-0000-0000-000000000020", 20L);
    QuizSet set = QuizSet.ready(7L, 20L, "바뀐 제목");
    ReflectionTestUtils.setField(set, "publicId", ready.getQuizSetId());
    LearningMaterial material = mock(LearningMaterial.class);
    when(material.getId()).thenReturn(20L);
    when(notifications.findPage(any(Long.class), any(), any(), any(), any()))
        .thenReturn(List.of(ready));
    when(notifications.countUnread(any(Long.class), any())).thenReturn(1L);
    when(quizSets.findAllByPublicIdInAndUserId(List.of(ready.getQuizSetId()), 7L))
        .thenReturn(List.of(set));
    when(materials.findAllByIdInAndUserId(List.of(20L), 7L)).thenReturn(List.of(material));

    var item = service.list(7L, null, 20).items().getFirst();

    assertTrue(item.targetAvailable());
    assertEquals("퀴즈 20", item.targetName());
    assertNull(item.failureCode());
  }

  @Test
  void failedNotificationUsesFailureActionAndOnlyNeedsItsMaterialToRemainAvailable() {
    QuizSet set = QuizSet.generating(7L, 21L, "실패 퀴즈");
    ReflectionTestUtils.setField(set, "publicId", "set-21");
    set.fail(QuizSetFailureCode.SOURCE_INSUFFICIENT);
    QuizGenerationNotification failed = QuizGenerationNotification.from(set);
    entity(failed, "00000000-0000-0000-0000-000000000021", 21L);
    when(notifications.findPage(any(Long.class), any(), any(), any(), any()))
        .thenReturn(List.of(failed));
    when(notifications.countUnread(any(Long.class), any())).thenReturn(1L);
    when(quizSets.findAllByPublicIdInAndUserId(any(), eq(7L))).thenReturn(List.of());
    when(materials.findAllByIdInAndUserId(List.of(21L), 7L)).thenReturn(List.of());

    var item = service.list(7L, null, 20).items().getFirst();

    assertFalse(item.targetAvailable());
    assertEquals("SOURCE_INSUFFICIENT", item.failureCode().name());
    assertEquals("RECONFIGURE_QUIZ", item.actionType().name());
  }

  @Test
  void rejectsAClientInventedOrExpiredCursorAsInvalidInput() {
    BusinessException error =
        assertThrows(BusinessException.class, () -> service.list(7L, "not-a-cursor", 20));

    assertEquals(CommonErrorCode.INVALID_INPUT, error.getErrorCode());
    assertEquals("cursor", error.getFields().getFirst().field());
  }

  @Test
  void rejectsAWellFormedCursorThatDoesNotBelongToTheCurrentUsersRetainedNotifications() {
    QuizGenerationNotification foreign =
        notification("00000000-0000-0000-0000-000000000020", 20L);

    BusinessException error =
        assertThrows(
            BusinessException.class,
            () -> service.list(7L, NotificationCursor.encode(foreign), 20));

    assertEquals(CommonErrorCode.INVALID_INPUT, error.getErrorCode());
    assertEquals("cursor", error.getFields().getFirst().field());
  }

  private QuizGenerationNotification notification(String publicId, long suffix) {
    QuizSet set = QuizSet.ready(7L, suffix, "퀴즈 " + suffix);
    ReflectionTestUtils.setField(set, "publicId", "set-" + suffix);
    QuizGenerationNotification notification = QuizGenerationNotification.from(set);
    entity(notification, publicId, suffix);
    return notification;
  }

  private void entity(QuizGenerationNotification notification, String publicId, long suffix) {
    ReflectionTestUtils.setField(notification, "publicId", publicId);
    ReflectionTestUtils.setField(notification, "id", suffix);
    ReflectionTestUtils.setField(
        notification, "createdAt", Instant.parse("2026-09-03T00:00:00Z").plusSeconds(suffix));
    ReflectionTestUtils.setField(
        notification, "updatedAt", Instant.parse("2026-09-03T00:00:00Z").plusSeconds(suffix));
  }
}
