package com.openmd.server.push.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.push.dto.request.PushDeviceRegistrationRequest;
import com.openmd.server.push.dto.request.PushDeviceRevokeRequest;
import com.openmd.server.push.dto.response.PushDeviceRegistrationResult;
import com.openmd.server.push.dto.response.PushDeviceRevokeResult;
import com.openmd.server.push.dto.response.PushDeviceStatusResult;
import com.openmd.server.push.service.PushDeviceService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push-devices")
@ConditionalOnProperty(name = "openmd.push.registration-enabled", havingValue = "true")
public class PushDeviceController {

  public static final String INSTALLATION_KEY_HEADER = "X-Push-Installation-Key";
  private final PushDeviceService service;

  public PushDeviceController(PushDeviceService service) {
    this.service = service;
  }

  @GetMapping("/{installationId}")
  public ApiResponse<PushDeviceStatusResult> status(
      @PathVariable String installationId,
      @RequestHeader(INSTALLATION_KEY_HEADER) String installationKey,
      @AuthenticationPrincipal AccessPrincipal principal) {
    return ApiResponse.success(
        service.status(principal.userId(), installationId, installationKey));
  }

  @PutMapping("/{installationId}")
  public ApiResponse<PushDeviceRegistrationResult> register(
      @PathVariable String installationId,
      @RequestHeader(INSTALLATION_KEY_HEADER) String installationKey,
      @Valid @RequestBody PushDeviceRegistrationRequest request,
      @AuthenticationPrincipal AccessPrincipal principal) {
    return ApiResponse.success(
        service.register(
            principal.userId(),
            principal.sessionId(),
            request.toCommand(installationId, installationKey)));
  }

  @PostMapping("/{installationId}/revoke")
  public ApiResponse<PushDeviceRevokeResult> revoke(
      @PathVariable String installationId,
      @RequestHeader(INSTALLATION_KEY_HEADER) String installationKey,
      @Valid @RequestBody PushDeviceRevokeRequest request) {
    return ApiResponse.success(service.revoke(request.toCommand(installationId, installationKey)));
  }
}
