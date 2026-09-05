package com.openmd.server.notification.dto.response;

import java.util.List;

public record NotificationPage(
    List<NotificationItem> items, long unreadCount, String nextCursor, boolean hasNext) {}
