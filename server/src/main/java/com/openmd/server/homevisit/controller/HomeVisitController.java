package com.openmd.server.homevisit.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.homevisit.dto.response.HomeVisitSummary;
import com.openmd.server.homevisit.service.HomeVisitService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/home-visits")
@ConditionalOnProperty(name = "openmd.home-visit.enabled", havingValue = "true", matchIfMissing = true)
public class HomeVisitController {
  private final HomeVisitService service;

  public HomeVisitController(HomeVisitService service) {
    this.service = service;
  }

  @PostMapping
  public ApiResponse<HomeVisitSummary> visit(
      @AuthenticationPrincipal AccessPrincipal principal) {
    return ApiResponse.success(service.visit(principal.userId()));
  }
}
