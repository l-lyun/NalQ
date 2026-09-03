package com.openmd.server.notification.dto.response;

import java.time.Instant;

public record NotificationReadResult(String notificationId, Instant readAt, long unreadCount) {}
