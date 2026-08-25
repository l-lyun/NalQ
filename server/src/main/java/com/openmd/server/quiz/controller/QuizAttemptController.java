package com.openmd.server.quiz.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.quiz.dto.model.QuizAttemptSubmissionResult;
import com.openmd.server.quiz.dto.request.EssayAssessmentRequest;
import com.openmd.server.quiz.dto.request.ShortAnswerGradingRequest;
import com.openmd.server.quiz.dto.request.SubmitQuizAttemptRequest;
import com.openmd.server.quiz.dto.response.EssayAssessmentResult;
import com.openmd.server.quiz.dto.response.PendingSelfAssessment;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.dto.response.SubmittedQuizAttempt;
import com.openmd.server.quiz.service.EssayAssessmentService;
import com.openmd.server.quiz.service.QuizAttemptResultService;
import com.openmd.server.quiz.service.QuizAttemptSubmissionService;
import com.openmd.server.quiz.service.ShortAnswerGradingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptController {

  private final QuizAttemptSubmissionService submissions;
  private final QuizAttemptResultService results;
  private final ShortAnswerGradingService gradings;
  private final EssayAssessmentService essayAssessments;

  public QuizAttemptController(
      QuizAttemptSubmissionService submissions,
      QuizAttemptResultService results,
      ShortAnswerGradingService gradings,
      EssayAssessmentService essayAssessments) {
    this.submissions = submissions;
    this.results = results;
    this.gradings = gradings;
    this.essayAssessments = essayAssessments;
  }

  @PutMapping("/quiz-sets/{quizSetId}/attempts/{attemptId}")
  public ResponseEntity<ApiResponse<SubmittedQuizAttempt>> submit(
      @AuthenticationPrincipal AccessPrincipal principal,
      @PathVariable String quizSetId,
      @PathVariable String attemptId,
      @RequestBody SubmitQuizAttemptRequest request) {
    QuizAttemptSubmissionResult submitted =
        submissions.submit(principal.userId(), quizSetId, attemptId, request.responses());
    HttpStatus status = submitted.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(ApiResponse.success(submitted.attempt()));
  }

  @GetMapping("/quiz-attempts/{attemptId}/result")
  public ResponseEntity<ApiResponse<QuizAttemptResult>> result(
      @AuthenticationPrincipal AccessPrincipal principal, @PathVariable String attemptId) {
    return ResponseEntity.ok(ApiResponse.success(results.result(principal.userId(), attemptId)));
  }

  @PutMapping("/quiz-attempts/{attemptId}/short-answer-gradings/{questionId}")
  public ResponseEntity<ApiResponse<QuizAttemptResult>> updateShortAnswerGrading(
      @AuthenticationPrincipal AccessPrincipal principal,
      @PathVariable String attemptId,
      @PathVariable String questionId,
      @RequestBody ShortAnswerGradingRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            gradings.update(principal.userId(), attemptId, questionId, request.outcome())));
  }

  @PutMapping("/quiz-attempts/{attemptId}/essay-assessments/{questionId}")
  public ResponseEntity<ApiResponse<EssayAssessmentResult>> assessEssay(
      @AuthenticationPrincipal AccessPrincipal principal,
      @PathVariable String attemptId,
      @PathVariable String questionId,
      @RequestBody EssayAssessmentRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            essayAssessments.assess(
                principal.userId(), attemptId, questionId, request.assessment())));
  }

  @GetMapping("/quiz-sets/{quizSetId}/attempts/pending-self-assessment")
  public ResponseEntity<ApiResponse<PendingSelfAssessment>> pending(
      @AuthenticationPrincipal AccessPrincipal principal, @PathVariable String quizSetId) {
    return ResponseEntity.ok(ApiResponse.success(results.pending(principal.userId(), quizSetId)));
  }
}
