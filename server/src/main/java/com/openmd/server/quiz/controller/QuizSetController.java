package com.openmd.server.quiz.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.quiz.dto.response.QuizSetView;
import com.openmd.server.quiz.service.QuizSetQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quiz-sets")
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizSetController {
  private final QuizSetQueryService service;

  public QuizSetController(QuizSetQueryService service) {
    this.service = service;
  }

  @GetMapping("/{quizSetId}")
  public ResponseEntity<ApiResponse<QuizSetView>> get(
      @AuthenticationPrincipal AccessPrincipal principal, @PathVariable String quizSetId) {
    return ResponseEntity.ok(ApiResponse.success(service.get(principal.userId(), quizSetId)));
  }
}
