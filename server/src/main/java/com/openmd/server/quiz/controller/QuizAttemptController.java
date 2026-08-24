package com.openmd.server.quiz.controller;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.api.ApiResponse;
import com.openmd.server.quiz.dto.request.ShortAnswerGradingRequest;
import com.openmd.server.quiz.dto.request.SubmitQuizAttemptRequest;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.dto.response.SubmittedQuizAttempt;
import com.openmd.server.quiz.dto.response.UpdatedShortAnswerGrading;
import com.openmd.server.quiz.service.QuizAttemptService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "openmd.quiz.enabled", havingValue = "true", matchIfMissing = true)
public class QuizAttemptController {

	private final QuizAttemptService service;

	public QuizAttemptController(QuizAttemptService service) {
		this.service = service;
	}

	@PostMapping("/quiz-sets/{quizSetId}/attempts")
	public ResponseEntity<ApiResponse<SubmittedQuizAttempt>> submit(
		@AuthenticationPrincipal AccessPrincipal principal,
		@PathVariable String quizSetId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody SubmitQuizAttemptRequest request
	) {
		SubmittedQuizAttempt submitted = service.submit(
			principal.userId(), quizSetId, idempotencyKey, request.responses()
		);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(submitted));
	}

	@GetMapping("/quiz-attempts/{attemptId}/result")
	public ApiResponse<QuizAttemptResult> result(
		@AuthenticationPrincipal AccessPrincipal principal,
		@PathVariable String attemptId
	) {
		return ApiResponse.success(service.result(principal.userId(), attemptId));
	}

	@PutMapping("/quiz-attempts/{attemptId}/short-answer-gradings/{questionId}")
	public ApiResponse<UpdatedShortAnswerGrading> updateShortAnswerGrading(
		@AuthenticationPrincipal AccessPrincipal principal,
		@PathVariable String attemptId,
		@PathVariable String questionId,
		@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
		@RequestBody ShortAnswerGradingRequest request
	) {
		return ApiResponse.success(service.updateShortAnswerGrading(
			principal.userId(), attemptId, questionId, idempotencyKey,
			request.outcome(), request.expectedRevision()
		));
	}
}
