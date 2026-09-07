package com.openmd.server.push.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.config.SecurityConfiguration;
import com.openmd.server.auth.repository.UserRepository;
import com.openmd.server.auth.security.AccessTokenService;
import com.openmd.server.push.dto.response.PushDeviceRevokeResult;
import com.openmd.server.push.service.PushDeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = PushDeviceSecurityTest.TestApplication.class,
    properties = {
      "springdoc.api-docs.enabled=false",
      "openmd.cors.allowed-origins=http://localhost:8081",
      "openmd.auth.browser.allowed-origins=http://localhost:5173",
      "openmd.push.registration-enabled=true",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
    })
@AutoConfigureMockMvc
class PushDeviceSecurityTest {

  private static final String INSTALLATION_ID = "11111111-1111-4111-8111-111111111111";
  private static final String INSTALLATION_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

  @Autowired MockMvc mockMvc;
  @MockitoBean PushDeviceService service;
  @MockitoBean AccessTokenService accessTokenService;
  @MockitoBean UserRepository userRepository;

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({
    PushDeviceController.class,
    PushDeviceExceptionHandler.class,
    SecurityConfiguration.class
  })
  static class TestApplication {}

  @Test
  void anonymousRevokeIgnoresAnExpiredBearerTokenAndUsesOnlyInstallationProof() throws Exception {
    when(service.revoke(any())).thenReturn(new PushDeviceRevokeResult(true));

    mockMvc
        .perform(
            post("/api/v1/push-devices/{installationId}/revoke", INSTALLATION_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
                .header(PushDeviceController.INSTALLATION_KEY_HEADER, INSTALLATION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRevoke()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.revoked").value(true));

    verifyNoInteractions(accessTokenService, userRepository);
  }

  @Test
  void invalidBearerCannotReachAuthenticatedRegistration() throws Exception {
    when(accessTokenService.verify("expired-token"))
        .thenThrow(new com.openmd.server.global.error.BusinessException(
            com.openmd.server.auth.error.AuthErrorCode.INVALID_CREDENTIAL));

    mockMvc
        .perform(
            put("/api/v1/push-devices/{installationId}", INSTALLATION_ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
                .header(PushDeviceController.INSTALLATION_KEY_HEADER, INSTALLATION_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error.code").value("AUTH_005"));

    verifyNoInteractions(service);
  }

  private String validRevoke() {
    return """
        {
          "operationId":"33333333-3333-4333-8333-333333333333",
          "operationIssuedAt":"2026-09-06T06:00:00Z",
          "bindingId":"55555555-5555-4555-8555-555555555555",
          "expectedRevision":1
        }
        """;
  }
}
