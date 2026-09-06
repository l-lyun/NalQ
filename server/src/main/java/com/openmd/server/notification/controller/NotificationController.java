package com.openmd.server.notification.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.notification.dto.request.ReadAllNotificationsRequest;
import com.openmd.server.notification.dto.response.NotificationPage;
import com.openmd.server.notification.dto.response.NotificationItem;
import com.openmd.server.notification.dto.response.NotificationReadAllResult;
import com.openmd.server.notification.dto.response.NotificationReadResult;
import com.openmd.server.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationController {
  private final NotificationService service;

  public NotificationController(NotificationService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<NotificationPage> list(
      @AuthenticationPrincipal AccessPrincipal principal,
      @RequestParam(required = false) String cursor) {
    return ApiResponse.success(service.list(principal.userId(), cursor, 20));
  }

  @GetMapping("/{notificationId}")
  public ApiResponse<NotificationItem> get(
      @AuthenticationPrincipal AccessPrincipal principal,
      @PathVariable String notificationId) {
    return ApiResponse.success(service.get(principal.userId(), notificationId));
  }

  @PutMapping("/{notificationId}/read")
  public ApiResponse<NotificationReadResult> read(
      @AuthenticationPrincipal AccessPrincipal principal,
      @PathVariable String notificationId) {
    return ApiResponse.success(service.read(principal.userId(), notificationId));
  }

  @PutMapping("/read-all")
  public ApiResponse<NotificationReadAllResult> readAll(
      @AuthenticationPrincipal AccessPrincipal principal,
      @Valid @RequestBody ReadAllNotificationsRequest request) {
    return ApiResponse.success(
        service.readAll(principal.userId(), request.throughNotificationId()));
  }
}
