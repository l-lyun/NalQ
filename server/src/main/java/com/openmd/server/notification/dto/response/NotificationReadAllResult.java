package com.openmd.server.notification.dto.response;

import java.time.Instant;

public record NotificationReadAllResult(Instant readAt, int updatedCount, long unreadCount) {}
