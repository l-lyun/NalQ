package com.openmd.server.quiz.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.dto.response.EssaySelfAssessmentSummary;
import com.openmd.server.quiz.dto.response.GradingCount;
import com.openmd.server.quiz.dto.response.ReviewAttemptResult;
import com.openmd.server.quiz.dto.response.ReviewAttemptSummary;
import com.openmd.server.quiz.dto.response.ReviewSessionStart;
import com.openmd.server.quiz.dto.response.ReviewSessionView;
import com.openmd.server.quiz.dto.response.ReviewSubmission;
import com.openmd.server.quiz.service.EssayAssessmentService;
import com.openmd.server.quiz.service.QuizAttemptResultService;
import com.openmd.server.quiz.service.QuizAttemptSubmissionService;
import com.openmd.server.quiz.service.QuizReviewService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class QuizReviewControllerTest {
  private final QuizReviewService reviews = mock(QuizReviewService.class);
  private final QuizAttemptSubmissionService submissions = mock(QuizAttemptSubmissionService.class);
  private final QuizAttemptResultService results = mock(QuizAttemptResultService.class);
  private final EssayAssessmentService essays = mock(EssayAssessmentService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(
                new QuizReviewController(reviews, submissions, results, essays))
            .setCustomArgumentResolvers(principal())
            .build();
  }

  @Test
  void startsForTheRequestedSourceAndUsesCreatedOrOkStatus() throws Exception {
    ReviewSessionView view = new ReviewSessionView("review-1", "source-1", "SOLVING", 1, List.of());
    when(reviews.start(7L, "source-1")).thenReturn(new ReviewSessionStart(true, view));

    mvc.perform(
            post("/api/v1/review-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAttemptId\":\"source-1\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.reviewSession.reviewSessionId").value("review-1"))
        .andExpect(jsonPath("$.data.reviewSession.status").value("SOLVING"));
    verify(reviews).start(7L, "source-1");

    when(reviews.start(7L, "source-1")).thenReturn(new ReviewSessionStart(false, view));
    mvc.perform(
            post("/api/v1/review-sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sourceAttemptId\":\"source-1\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void reviewSubmissionUsesReviewIdentifiersAndSubmittedAt() throws Exception {
    when(submissions.submitReview(7L, "review-1", List.of()))
        .thenReturn(
            new ReviewSubmission(
                "review-1",
                QuizAttemptStatus.COMPLETED.name(),
                new GradingCount(1, 1),
                List.of(),
                Instant.parse("2026-08-24T05:20:00Z")));

    mvc.perform(
            put("/api/v1/review-sessions/review-1/submission")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responses\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reviewSessionId").value("review-1"))
        .andExpect(jsonPath("$.data.attemptId").doesNotExist())
        .andExpect(jsonPath("$.data.submittedAt").value("2026-08-24T05:20:00Z"));
  }

  @Test
  void reviewResultSeparatesResolvedAndUnresolvedCounts() throws Exception {
    when(results.reviewResult(7L, "review-1"))
        .thenReturn(
            new ReviewAttemptResult(
                "review-1",
                "source-1",
                "COMPLETED",
                new ReviewAttemptSummary(
                    new GradingCount(1, 2), new EssaySelfAssessmentSummary(0, 1, 0), 1, 2),
                List.of()));

    mvc.perform(get("/api/v1/review-sessions/review-1/result"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.summary.resolvedQuestionCount").value(1))
        .andExpect(jsonPath("$.data.summary.unresolvedQuestionCount").value(2))
        .andExpect(jsonPath("$.data.summary.reviewQuestionCount").doesNotExist());
  }

  private HandlerMethodArgumentResolver principal() {
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
        return new AccessPrincipal(7L, "session");
      }
    };
  }
}
