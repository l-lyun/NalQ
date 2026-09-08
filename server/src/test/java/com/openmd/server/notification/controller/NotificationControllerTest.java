package com.openmd.server.notification.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.error.BusinessException;
import com.openmd.server.global.error.CommonErrorCode;
import com.openmd.server.global.error.GlobalExceptionHandler;
import com.openmd.server.notification.dto.response.NotificationPage;
import com.openmd.server.notification.dto.response.NotificationItem;
import com.openmd.server.notification.domain.NotificationType;
import com.openmd.server.notification.domain.NotificationActionType;
import com.openmd.server.notification.dto.response.NotificationReadAllResult;
import com.openmd.server.notification.dto.response.NotificationReadResult;
import com.openmd.server.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class NotificationControllerTest {
  private static final String NOTIFICATION_ID = "00000000-0000-4000-8000-000000000001";
  private final NotificationService service = mock(NotificationService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.standaloneSetup(new NotificationController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(principal())
        .build();
  }

  @Test
  void exposesAnAuthenticatedSingleNotificationLookupRoute() throws Exception {
    when(service.get(7L, NOTIFICATION_ID)).thenReturn(new NotificationItem(
        NOTIFICATION_ID, 1, NotificationType.QUIZ_GENERATION_READY, "quiz-1", "20",
        "퀴즈 제목", null, NotificationActionType.FOCUS_QUIZ_IN_LIST, false,
        null, Instant.parse("2026-09-06T00:00:00Z")));
    mvc.perform(get("/api/v1/notifications/" + NOTIFICATION_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notificationId").value(NOTIFICATION_ID))
        .andExpect(jsonPath("$.data.targetName").value("퀴즈 제목"))
        .andExpect(jsonPath("$.data.targetAvailable").value(false))
        .andExpect(jsonPath("$.data.actionType").value("FOCUS_QUIZ_IN_LIST"));
    verify(service).get(7L, NOTIFICATION_ID);
  }

  @Test
  void hidesUnavailableOrForeignSingleNotifications() throws Exception {
    when(service.get(7L, NOTIFICATION_ID))
        .thenThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
    mvc.perform(get("/api/v1/notifications/" + NOTIFICATION_ID))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error.code").value("COMMON_003"));
  }

  @Test
  void listsTheLatestTwentyNotifications() throws Exception {
    when(service.list(7L, "cursor", 20))
        .thenReturn(new NotificationPage(List.of(), 3L, "next", true));

    mvc.perform(get("/api/v1/notifications").param("cursor", "cursor"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.unreadCount").value(3))
        .andExpect(jsonPath("$.data.nextCursor").value("next"))
        .andExpect(jsonPath("$.data.hasNext").value(true));
  }

  @Test
  void marksOneNotificationRead() throws Exception {
    Instant readAt = Instant.parse("2026-09-03T01:07:00Z");
    when(service.read(7L, "notification-1"))
        .thenReturn(new NotificationReadResult("notification-1", readAt, 2L));

    mvc.perform(put("/api/v1/notifications/notification-1/read"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notificationId").value("notification-1"))
        .andExpect(jsonPath("$.data.readAt").value("2026-09-03T01:07:00Z"))
        .andExpect(jsonPath("$.data.unreadCount").value(2));
  }

  @Test
  void marksOnlyThroughTheVisibleNotificationRead() throws Exception {
    Instant readAt = Instant.parse("2026-09-03T01:08:00Z");
    when(service.readAll(7L, NOTIFICATION_ID))
        .thenReturn(new NotificationReadAllResult(readAt, 4, 1L));

    mvc.perform(
            put("/api/v1/notifications/read-all")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"throughNotificationId\":\"" + NOTIFICATION_ID + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedCount").value(4))
        .andExpect(jsonPath("$.data.unreadCount").value(1));
    verify(service).readAll(7L, NOTIFICATION_ID);
  }

  @Test
  void rejectsAMalformedReadAllBoundaryBeforeCallingTheService() throws Exception {
    mvc.perform(
            put("/api/v1/notifications/read-all")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"throughNotificationId\":\"not-a-uuid\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_001"));
  }

  @Test
  void returnsCommonInvalidInputForAMalformedCursor() throws Exception {
    when(service.list(7L, "bad", 20))
        .thenThrow(new BusinessException(CommonErrorCode.INVALID_INPUT));

    mvc.perform(get("/api/v1/notifications").param("cursor", "bad"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_001"));
  }

  private HandlerMethodArgumentResolver principal() {
    return new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == AccessPrincipal.class;
      }

      @Override
      public Object resolveArgument(
          MethodParameter parameter,
          ModelAndViewContainer container,
          NativeWebRequest request,
          org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
        return new AccessPrincipal(7L, "session");
      }
    };
  }
}
