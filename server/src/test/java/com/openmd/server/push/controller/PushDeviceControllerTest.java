package com.openmd.server.push.controller;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.error.GlobalExceptionHandler;
import com.openmd.server.push.domain.PushDeviceStatus;
import com.openmd.server.push.domain.PushPlatform;
import com.openmd.server.push.dto.command.RegisterPushDeviceCommand;
import com.openmd.server.push.dto.command.RevokePushDeviceCommand;
import com.openmd.server.push.dto.response.PushDeviceRegistrationResult;
import com.openmd.server.push.dto.response.PushDeviceRevokeResult;
import com.openmd.server.push.dto.response.PushDeviceStatusResult;
import com.openmd.server.push.service.PushDeviceService;
import com.openmd.server.push.error.PushRateLimitedException;
import java.time.Instant;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class PushDeviceControllerTest {

  private static final String INSTALLATION_ID = "11111111-1111-4111-8111-111111111111";
  private static final String KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
  private final PushDeviceService service = mock(PushDeviceService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PushDeviceController(service))
            .setControllerAdvice(new PushDeviceExceptionHandler(), new GlobalExceptionHandler())
            .setCustomArgumentResolvers(accessPrincipalResolver())
            .build();
  }

  @Test
  void returnsOnlyTheCurrentAccountsBindingFromStatusLookup() throws Exception {
    when(service.status(42L, INSTALLATION_ID, KEY))
        .thenReturn(
            new PushDeviceStatusResult(
                3L, true, "44444444-4444-4444-8444-444444444444", PushDeviceStatus.ACTIVE, PushPlatform.IOS));

    mockMvc
        .perform(
            get("/api/v1/push-devices/{installationId}", INSTALLATION_ID)
                .header("X-Push-Installation-Key", KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.revision").value(3))
        .andExpect(jsonPath("$.data.belongsToCurrentUser").value(true))
        .andExpect(jsonPath("$.data.bindingId").value("44444444-4444-4444-8444-444444444444"));
  }

  @Test
  void registersUsingOnlyTheAuthenticatedPrincipalAsTheUserSource() throws Exception {
    when(service.register(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.eq("session-42"), org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            new PushDeviceRegistrationResult(
                INSTALLATION_ID,
                1L,
                "44444444-4444-4444-8444-444444444444",
                PushDeviceStatus.ACTIVE,
                42L));

    mockMvc
        .perform(
            put("/api/v1/push-devices/{installationId}", INSTALLATION_ID)
                .header("X-Push-Installation-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "operationId":"33333333-3333-4333-8333-333333333333",
                      "operationIssuedAt":"2026-09-06T06:00:00Z",
                      "expectedRevision":0,
                      "platform":"IOS",
                      "provider":"EXPO",
                      "pushToken":"ExponentPushToken[aaaaaaaaaaaaaaaaaaaaaa]",
                      "permission":"GRANTED"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.userId").value(42));

    verify(service)
        .register(
            org.mockito.ArgumentMatchers.eq(42L),
            org.mockito.ArgumentMatchers.eq("session-42"),
            org.mockito.ArgumentMatchers.any(RegisterPushDeviceCommand.class));
  }

  @Test
  void rejectsMissingOperationFieldsBeforeCallingTheService() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/push-devices/{installationId}", INSTALLATION_ID)
                .header("X-Push-Installation-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedRevision\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_001"));

    verifyNoInteractions(service);
  }

  @Test
  void allowsTheLimitedRevokeWithoutAUserPrincipal() throws Exception {
    when(service.revoke(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new PushDeviceRevokeResult(true));

    mockMvc
        .perform(
            post("/api/v1/push-devices/{installationId}/revoke", INSTALLATION_ID)
                .header("X-Push-Installation-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "operationId":"33333333-3333-4333-8333-333333333333",
                      "operationIssuedAt":"2026-09-06T06:00:00Z",
                      "bindingId":"44444444-4444-4444-8444-444444444444",
                      "expectedRevision":1
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.revoked").value(true));

    verify(service).revoke(org.mockito.ArgumentMatchers.any(RevokePushDeviceCommand.class));
  }

  @Test
  void authenticatedEndpointsResolveTheSecurityPrincipalRatherThanRequestFields() throws Exception {
    Method status =
        PushDeviceController.class.getMethod(
            "status", String.class, String.class, AccessPrincipal.class);
    Method register =
        PushDeviceController.class.getMethod(
            "register",
            String.class,
            String.class,
            com.openmd.server.push.dto.request.PushDeviceRegistrationRequest.class,
            AccessPrincipal.class);

    assertNotNull(status.getParameters()[2].getAnnotation(AuthenticationPrincipal.class));
    assertNotNull(register.getParameters()[3].getAnnotation(AuthenticationPrincipal.class));
  }

  @Test
  void rateLimitResponseIncludesTheRetryDelayWithoutExposingCredentials() throws Exception {
    when(service.revoke(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new PushRateLimitedException(19L));

    mockMvc
        .perform(
            post("/api/v1/push-devices/{installationId}/revoke", INSTALLATION_ID)
                .header("X-Push-Installation-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "operationId":"33333333-3333-4333-8333-333333333333",
                      "operationIssuedAt":"2026-09-06T06:00:00Z",
                      "bindingId":"44444444-4444-4444-8444-444444444444",
                      "expectedRevision":1
                    }
                    """))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "19"))
        .andExpect(jsonPath("$.error.code").value("PUSH_RATE_LIMITED"));
  }

  private HandlerMethodArgumentResolver accessPrincipalResolver() {
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
        return new AccessPrincipal(42L, "session-42");
      }
    };
  }
}
