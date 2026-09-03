package com.openmd.server.notification.domain;

import com.openmd.server.global.entity.BaseEntity;
import com.openmd.server.quiz.domain.entity.QuizSet;
import com.openmd.server.quiz.domain.type.QuizSetFailureCode;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class QuizGenerationNotification extends BaseEntity {
  public static final int PAYLOAD_VERSION = 1;

  @Column(name = "public_id", nullable = false, updatable = false, length = 36, unique = true)
  private String publicId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private long userId;

  @Column(name = "payload_version", nullable = false, updatable = false)
  private int payloadVersion;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, updatable = false, length = 32)
  private NotificationType type;

  @Column(name = "quiz_set_id", nullable = false, updatable = false, length = 36, unique = true)
  private String quizSetId;

  @Column(name = "material_id", nullable = false, updatable = false, length = 36)
  private String materialId;

  @Column(name = "target_name", nullable = false, updatable = false, length = 255)
  private String targetName;

  @Enumerated(EnumType.STRING)
  @Column(name = "failure_code", updatable = false, length = 32)
  private QuizSetFailureCode failureCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, updatable = false, length = 32)
  private NotificationActionType actionType;

  @Column(name = "read_at")
  private Instant readAt;

  protected QuizGenerationNotification() {}

  private QuizGenerationNotification(
      long userId,
      NotificationType type,
      String quizSetId,
      String materialId,
      String targetName,
      QuizSetFailureCode failureCode,
      NotificationActionType actionType) {
    this.publicId = UUID.randomUUID().toString();
    this.userId = userId;
    this.payloadVersion = PAYLOAD_VERSION;
    this.type = type;
    this.quizSetId = quizSetId;
    this.materialId = materialId;
    this.targetName = targetName;
    this.failureCode = failureCode;
    this.actionType = actionType;
  }

  public static QuizGenerationNotification from(QuizSet quizSet) {
    if (quizSet.getStatus() == QuizSetStatus.READY) {
      return new QuizGenerationNotification(
          quizSet.getUserId(),
          NotificationType.QUIZ_GENERATION_READY,
          quizSet.getPublicId(),
          Long.toString(quizSet.getLearningMaterialId()),
          quizSet.getQuizTitle(),
          null,
          NotificationActionType.FOCUS_QUIZ_IN_LIST);
    }
    if (quizSet.getStatus() == QuizSetStatus.FAILED && quizSet.getFailureCode() != null) {
      return new QuizGenerationNotification(
          quizSet.getUserId(),
          NotificationType.QUIZ_GENERATION_FAILED,
          quizSet.getPublicId(),
          Long.toString(quizSet.getLearningMaterialId()),
          quizSet.getQuizTitle(),
          quizSet.getFailureCode(),
          NotificationActionType.RECONFIGURE_QUIZ);
    }
    throw new IllegalArgumentException("Quiz set must be terminal before creating a notification");
  }

  public void markRead(Instant readAt) {
    if (this.readAt == null) this.readAt = readAt;
  }

  public String getPublicId() {
    return publicId;
  }

  public long getUserId() {
    return userId;
  }

  public int getPayloadVersion() {
    return payloadVersion;
  }

  public NotificationType getType() {
    return type;
  }

  public String getQuizSetId() {
    return quizSetId;
  }

  public String getMaterialId() {
    return materialId;
  }

  public String getTargetName() {
    return targetName;
  }

  public QuizSetFailureCode getFailureCode() {
    return failureCode;
  }

  public NotificationActionType getActionType() {
    return actionType;
  }

  public Instant getReadAt() {
    return readAt;
  }
}
