package com.openmd.server.quiz.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.quiz.dto.response.QuizSetView;
import com.openmd.server.quiz.dto.response.QuizSetPage;
import com.openmd.server.quiz.dto.response.RenamedQuizSet;
import com.openmd.server.quiz.dto.request.RenameQuizSetRequest;
import com.openmd.server.quiz.service.QuizSetManagementService;
import com.openmd.server.quiz.service.QuizSetQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quiz-sets")
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizSetController {
  private final QuizSetQueryService service;
  private final QuizSetManagementService management;

  public QuizSetController(QuizSetQueryService service, QuizSetManagementService management) {
    this.service = service;
    this.management = management;
  }

  @GetMapping
  public ApiResponse<QuizSetPage> list(
      @AuthenticationPrincipal AccessPrincipal principal,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "6") int size,
      @RequestParam(required = false) String query) {
    return ApiResponse.success(service.list(principal.userId(), page, size, query));
  }

  @GetMapping("/{quizSetId}")
  public ResponseEntity<ApiResponse<QuizSetView>> get(
      @AuthenticationPrincipal AccessPrincipal principal, @PathVariable String quizSetId) {
    return ResponseEntity.ok(ApiResponse.success(service.get(principal.userId(), quizSetId)));
  }

  @PatchMapping("/{quizSetId}")
  public ApiResponse<RenamedQuizSet> rename(
      @AuthenticationPrincipal AccessPrincipal principal,
      @PathVariable String quizSetId,
      @RequestBody RenameQuizSetRequest request) {
    return ApiResponse.success(
        management.rename(
            principal.userId(), quizSetId, request == null ? null : request.quizTitle()));
  }
}
