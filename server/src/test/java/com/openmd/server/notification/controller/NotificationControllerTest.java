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
    when(service.readAll(7L, "notification-1"))
        .thenReturn(new NotificationReadAllResult(readAt, 4, 1L));

    mvc.perform(
            put("/api/v1/notifications/read-all")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"throughNotificationId\":\"notification-1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.updatedCount").value(4))
        .andExpect(jsonPath("$.data.unreadCount").value(1));
    verify(service).readAll(7L, "notification-1");
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
