package com.openmd.server.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReadAllNotificationsRequest(
    @NotBlank(message = "throughNotificationId는 필수입니다.")
        @Pattern(
            regexp =
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89aAbB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
            message = "throughNotificationId 형식이 올바르지 않습니다.")
        String throughNotificationId) {}
