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
import com.openmd.server.global.error.GlobalExceptionHandler;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.dto.response.EssaySelfAssessmentSummary;
import com.openmd.server.quiz.dto.response.GradingCount;
import com.openmd.server.quiz.dto.response.ReviewAttemptResult;
import com.openmd.server.quiz.dto.response.ReviewAttemptSummary;
import com.openmd.server.quiz.dto.response.ReviewCandidateItem;
import com.openmd.server.quiz.dto.response.ReviewCandidateList;
import com.openmd.server.quiz.dto.response.ReviewLatestView;
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
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void startsForTheRequestedSourceAndUsesCreatedOrOkStatus() throws Exception {
    ReviewSessionView view =
        new ReviewSessionView("review-1", "source-1", "SOLVING", 1, List.of(), List.of());
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
  void returnsPendingEssayQuestionIdsWhenResumingAReviewSession() throws Exception {
    ReviewSessionView view =
        new ReviewSessionView(
            "review-1",
            "source-1",
            "SELF_ASSESSMENT_REQUIRED",
            1,
            List.of("question-1"),
            List.of());
    when(reviews.get(7L, "review-1")).thenReturn(view);

    mvc.perform(get("/api/v1/review-sessions/review-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SELF_ASSESSMENT_REQUIRED"))
        .andExpect(jsonPath("$.data.pendingEssayQuestionIds[0]").value("question-1"));
  }

  @Test
  void latestReviewIncludesTheMaterialAndFullQuizContext() throws Exception {
    when(reviews.latest(7L))
        .thenReturn(
            new ReviewLatestView(
                "source-1",
                "quiz-set-1",
                2,
                "운영체제 중간고사 대비",
                "운영체제 핵심 정리",
                Instant.parse("2026-08-26T00:20:00Z"),
                10,
                3,
                null));

    mvc.perform(get("/api/v1/quiz-reviews/latest"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quizTitle").value("운영체제 중간고사 대비"))
        .andExpect(jsonPath("$.data.materialTitle").value("운영체제 핵심 정리"))
        .andExpect(jsonPath("$.data.completedAt").value("2026-08-26T00:20:00Z"))
        .andExpect(jsonPath("$.data.totalQuestionCount").value(10))
        .andExpect(jsonPath("$.data.reviewQuestionCount").value(3));
  }

  @Test
  void listsReviewCandidatesForTheLearningMain() throws Exception {
    when(reviews.candidates(7L, 3))
        .thenReturn(
            new ReviewCandidateList(
                List.of(
                    new ReviewCandidateItem(
                        "quiz-set-1",
                        "운영체제 퀴즈",
                        "운영체제",
                        "source-1",
                        "pending-1",
                        "review-1",
                        2,
                        Instant.parse("2026-08-28T12:30:00Z")))));

    mvc.perform(get("/api/v1/quiz-reviews/candidates"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].quizSetId").value("quiz-set-1"))
        .andExpect(jsonPath("$.data.items[0].quizTitle").value("운영체제 퀴즈"))
        .andExpect(jsonPath("$.data.items[0].materialTitle").value("운영체제"))
        .andExpect(jsonPath("$.data.items[0].sourceAttemptId").value("source-1"))
        .andExpect(jsonPath("$.data.items[0].pendingSelfAssessmentAttemptId").value("pending-1"))
        .andExpect(jsonPath("$.data.items[0].activeReviewSessionId").value("review-1"))
        .andExpect(jsonPath("$.data.items[0].reviewQuestionCount").value(2))
        .andExpect(
            jsonPath("$.data.items[0].lastLearningActivityAt")
                .value("2026-08-28T12:30:00Z"));
    verify(reviews).candidates(7L, 3);
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
                false,
                new ReviewAttemptSummary(
                    new GradingCount(1, 2), new EssaySelfAssessmentSummary(0, 1, 0), 1, 2),
                List.of()));

    mvc.perform(get("/api/v1/review-sessions/review-1/result"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reviewAvailable").value(false))
        .andExpect(jsonPath("$.data.summary.resolvedQuestionCount").value(1))
        .andExpect(jsonPath("$.data.summary.unresolvedQuestionCount").value(2))
        .andExpect(jsonPath("$.data.summary.reviewQuestionCount").doesNotExist());
  }

  @Test
  void missingSourceAttemptIdIsReportedAsAnInvalidField() throws Exception {
    mvc.perform(
            post("/api/v1/review-sessions").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_001"))
        .andExpect(jsonPath("$.error.fields[0].field").value("sourceAttemptId"));
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
