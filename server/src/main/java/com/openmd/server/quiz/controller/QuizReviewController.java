package com.openmd.server.quiz.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.quiz.dto.request.EssayAssessmentRequest;
import com.openmd.server.quiz.dto.request.StartReviewSessionRequest;
import com.openmd.server.quiz.dto.request.SubmitQuizAttemptRequest;
import com.openmd.server.quiz.dto.response.*;
import com.openmd.server.quiz.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizReviewController {
  private final QuizReviewService reviews;
  private final QuizAttemptSubmissionService submissions;
  private final QuizAttemptResultService results;
  private final EssayAssessmentService essays;

  public QuizReviewController(
      QuizReviewService reviews,
      QuizAttemptSubmissionService submissions,
      QuizAttemptResultService results,
      EssayAssessmentService essays) {
    this.reviews = reviews;
    this.submissions = submissions;
    this.results = results;
    this.essays = essays;
  }

  @GetMapping("/quiz-reviews/latest")
  public ResponseEntity<ApiResponse<ReviewLatestView>> latest(
      @AuthenticationPrincipal AccessPrincipal p) {
    return ResponseEntity.ok(ApiResponse.success(reviews.latest(p.userId())));
  }

  @GetMapping("/quiz-reviews/candidates")
  public ApiResponse<ReviewCandidateList> candidates(
      @AuthenticationPrincipal AccessPrincipal principal,
      @RequestParam(defaultValue = "3") int limit) {
    return ApiResponse.success(reviews.candidates(principal.userId(), limit));
  }

  @PostMapping("/review-sessions")
  public ResponseEntity<ApiResponse<ReviewSessionEnvelope>> start(
      @AuthenticationPrincipal AccessPrincipal principal,
      @RequestBody StartReviewSessionRequest request) {
    ReviewSessionStart started =
        reviews.start(principal.userId(), request.requiredSourceAttemptId());
    return ResponseEntity.status(started.created() ? 201 : 200)
        .body(ApiResponse.success(new ReviewSessionEnvelope(started.reviewSession())));
  }

  @GetMapping("/review-sessions/{id}")
  public ResponseEntity<ApiResponse<ReviewSessionView>> get(
      @AuthenticationPrincipal AccessPrincipal p, @PathVariable String id) {
    return ResponseEntity.ok(ApiResponse.success(reviews.get(p.userId(), id)));
  }

  @PutMapping("/review-sessions/{id}/submission")
  public ResponseEntity<ApiResponse<ReviewSubmission>> submit(
      @AuthenticationPrincipal AccessPrincipal p,
      @PathVariable String id,
      @RequestBody SubmitQuizAttemptRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(submissions.submitReview(p.userId(), id, request.responses())));
  }

  @GetMapping("/review-sessions/{id}/result")
  public ResponseEntity<ApiResponse<ReviewAttemptResult>> result(
      @AuthenticationPrincipal AccessPrincipal p, @PathVariable String id) {
    return ResponseEntity.ok(ApiResponse.success(results.reviewResult(p.userId(), id)));
  }

  @PutMapping("/review-sessions/{id}/essay-assessments/{questionId}")
  public ResponseEntity<ApiResponse<ReviewEssayAssessment>> assess(
      @AuthenticationPrincipal AccessPrincipal p,
      @PathVariable String id,
      @PathVariable String questionId,
      @RequestBody EssayAssessmentRequest request) {
    EssayAssessmentResult assessed =
        essays.assessReview(p.userId(), id, questionId, request.assessment());
    return ResponseEntity.ok(
        ApiResponse.success(
            new ReviewEssayAssessment(
                assessed.questionId(),
                assessed.assessment(),
                assessed.assessment() == com.openmd.server.quiz.domain.type.GradingOutcome.CORRECT
                    ? "RESOLVED"
                    : "UNRESOLVED",
                assessed.status().name(),
                assessed.remainingSelfAssessmentCount())));
  }
}
