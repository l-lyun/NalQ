package com.openmd.server.quiz.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.quiz.dto.request.GenerateQuizRequest;
import com.openmd.server.quiz.dto.response.AcceptedQuizGeneration;
import com.openmd.server.quiz.dto.response.ActiveQuizGeneration;
import com.openmd.server.quiz.service.QuizGenerationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning-materials/{materialId}/quiz-sets")
@ConditionalOnProperty(
    name = {"openmd.quiz.enabled", "openmd.quiz.generation.enabled"},
    havingValue = "true",
    matchIfMissing = true)
public class QuizGenerationController {
  private final QuizGenerationService service;

  public QuizGenerationController(QuizGenerationService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<AcceptedQuizGeneration>> accept(
      @AuthenticationPrincipal AccessPrincipal principal,
      @PathVariable String materialId,
      @RequestBody GenerateQuizRequest request) {
    AcceptedQuizGeneration accepted =
        service.accept(principal.userId(), materialId, request.toCommand());
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(accepted));
  }

  @GetMapping("/active")
  public ResponseEntity<ApiResponse<ActiveQuizGeneration>> active(
      @AuthenticationPrincipal AccessPrincipal principal, @PathVariable String materialId) {
    return ResponseEntity.ok(ApiResponse.success(service.active(principal.userId(), materialId)));
  }
}
