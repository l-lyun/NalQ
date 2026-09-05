package com.openmd.server.notification.dto.response;

import com.openmd.server.notification.domain.NotificationActionType;
import com.openmd.server.notification.domain.NotificationType;
import com.openmd.server.quiz.domain.type.QuizSetFailureCode;
import java.time.Instant;

public record NotificationItem(
    String notificationId,
    int payloadVersion,
    NotificationType type,
    String quizSetId,
    String materialId,
    String targetName,
    QuizSetFailureCode failureCode,
    NotificationActionType actionType,
    boolean targetAvailable,
    Instant readAt,
    Instant createdAt) {}
