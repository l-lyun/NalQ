package com.openmd.server.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReadAllNotificationsRequest(
    @NotBlank(message = "throughNotificationId는 필수입니다.") String throughNotificationId) {}
