package com.openmd.server.notification.service;

import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.global.api.FieldError;
import com.openmd.server.learningmaterial.domain.LearningMaterial;
import com.openmd.server.learningmaterial.repository.LearningMaterialRepository;
import com.openmd.server.notification.domain.NotificationType;
import com.openmd.server.notification.domain.QuizGenerationNotification;
import com.openmd.server.notification.dto.response.NotificationItem;
import com.openmd.server.notification.dto.response.NotificationPage;
import com.openmd.server.notification.dto.response.NotificationReadAllResult;
import com.openmd.server.notification.dto.response.NotificationReadResult;
import com.openmd.server.notification.repository.NotificationRepository;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.repository.QuizSetRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationService {
  private static final Duration RETENTION = Duration.ofDays(90);
  private static final int PAGE_SIZE = 20;

  private final NotificationRepository notifications;
  private final QuizSetRepository quizSets;
  private final LearningMaterialRepository materials;
  private final Clock clock;

  @Autowired
  public NotificationService(
      NotificationRepository notifications,
      QuizSetRepository quizSets,
      LearningMaterialRepository materials,
      ObjectProvider<Clock> clocks) {
    this(notifications, quizSets, materials, clocks.getIfAvailable(Clock::systemUTC));
  }

  NotificationService(
      NotificationRepository notifications,
      QuizSetRepository quizSets,
      LearningMaterialRepository materials,
      Clock clock) {
    this.notifications = notifications;
    this.quizSets = quizSets;
    this.materials = materials;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public NotificationPage list(long userId, String encodedCursor, int requestedSize) {
    int size = Math.min(requestedSize, PAGE_SIZE);
    NotificationCursor cursor = NotificationCursor.decode(encodedCursor);
    Instant retainedSince = retainedSince();
    validateCursor(userId, cursor, retainedSince);
    List<QuizGenerationNotification> fetched =
        notifications.findPage(
            userId,
            retainedSince,
            cursor == null ? null : cursor.createdAt(),
            cursor == null ? null : cursor.notificationId(),
            PageRequest.of(0, size + 1));
    boolean hasNext = fetched.size() > size;
    List<QuizGenerationNotification> page =
        hasNext ? fetched.subList(0, size) : fetched;
    Availability availability = availability(userId, page);
    List<NotificationItem> items =
        page.stream().map(notification -> item(notification, availability)).toList();
    String nextCursor =
        hasNext && !page.isEmpty() ? NotificationCursor.encode(page.getLast()) : null;
    return new NotificationPage(
        items, notifications.countUnread(userId, retainedSince), nextCursor, hasNext);
  }

  @Transactional
  public NotificationReadResult read(long userId, String notificationId) {
    Instant retainedSince = retainedSince();
    QuizGenerationNotification notification = owned(notificationId, userId, retainedSince);
    notification.markRead(clock.instant());
    return new NotificationReadResult(
        notification.getPublicId(),
        notification.getReadAt(),
        notifications.countUnread(userId, retainedSince));
  }

  @Transactional
  public NotificationReadAllResult readAll(long userId, String throughNotificationId) {
    Instant retainedSince = retainedSince();
    QuizGenerationNotification boundary =
        owned(throughNotificationId, userId, retainedSince);
    Instant readAt = clock.instant();
    int updated =
        notifications.markUnreadThrough(
            userId,
            retainedSince,
            boundary.getCreatedAt(),
            boundary.getPublicId(),
            readAt);
    return new NotificationReadAllResult(
        readAt, updated, notifications.countUnread(userId, retainedSince));
  }

  private QuizGenerationNotification owned(
      String notificationId, long userId, Instant retainedSince) {
    return notifications
        .findOwnedRetained(notificationId, userId, retainedSince)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
  }

  private Instant retainedSince() {
    return clock.instant().minus(RETENTION);
  }

  private void validateCursor(long userId, NotificationCursor cursor, Instant retainedSince) {
    if (cursor == null) return;
    boolean valid =
        notifications
            .findOwnedRetained(cursor.notificationId(), userId, retainedSince)
            .map(notification -> notification.getCreatedAt().equals(cursor.createdAt()))
            .orElse(false);
    if (!valid) {
      throw new BusinessException(
          CommonErrorCode.INVALID_INPUT,
          List.of(new FieldError("cursor", "cursor가 올바르지 않습니다.")));
    }
  }

  private Availability availability(
      long userId, List<QuizGenerationNotification> notifications) {
    List<String> quizSetIds =
        notifications.stream().map(QuizGenerationNotification::getQuizSetId).distinct().toList();
    Set<String> readyQuizSetIds = new HashSet<>();
    if (!quizSetIds.isEmpty()) {
      for (QuizSet quizSet : quizSets.findAllByPublicIdInAndUserId(quizSetIds, userId)) {
        if (quizSet.getStatus() == QuizSetStatus.READY) {
          readyQuizSetIds.add(quizSet.getPublicId());
        }
      }
    }

    List<Long> materialIds =
        notifications.stream()
            .map(QuizGenerationNotification::getMaterialId)
            .map(Long::parseLong)
            .distinct()
            .toList();
    Set<Long> availableMaterialIds = new HashSet<>();
    if (!materialIds.isEmpty()) {
      for (LearningMaterial material : materials.findAllByIdInAndUserId(materialIds, userId)) {
        availableMaterialIds.add(material.getId());
      }
    }
    return new Availability(readyQuizSetIds, availableMaterialIds);
  }

  private NotificationItem item(
      QuizGenerationNotification notification, Availability availability) {
    boolean targetAvailable =
        notification.getType() == NotificationType.QUIZ_GENERATION_READY
            ? availability.readyQuizSetIds().contains(notification.getQuizSetId())
            : availability.availableMaterialIds().contains(
                Long.parseLong(notification.getMaterialId()));
    return new NotificationItem(
        notification.getPublicId(),
        notification.getPayloadVersion(),
        notification.getType(),
        notification.getQuizSetId(),
        notification.getMaterialId(),
        notification.getTargetName(),
        notification.getFailureCode(),
        notification.getActionType(),
        targetAvailable,
        notification.getReadAt(),
        notification.getCreatedAt());
  }

  private record Availability(Set<String> readyQuizSetIds, Set<Long> availableMaterialIds) {}
}
