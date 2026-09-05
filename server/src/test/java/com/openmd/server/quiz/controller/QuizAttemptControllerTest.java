package com.openmd.server.quiz.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.error.GlobalExceptionHandler;
import com.openmd.server.quiz.domain.type.GradingOutcome;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizAttemptStatus;
import com.openmd.server.quiz.dto.model.QuizAttemptSubmissionResult;
import com.openmd.server.quiz.dto.response.AnswerValue;
import com.openmd.server.quiz.dto.response.EssaySelfAssessmentSummary;
import com.openmd.server.quiz.dto.response.GradingCount;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.dto.response.QuizAttemptSummary;
import com.openmd.server.quiz.dto.response.QuizQuestionResultView;
import com.openmd.server.quiz.dto.response.SubmittedQuizAttempt;
import com.openmd.server.quiz.service.EssayAssessmentService;
import com.openmd.server.quiz.service.QuizAttemptResultService;
import com.openmd.server.quiz.service.QuizAttemptSubmissionService;
import com.openmd.server.quiz.service.GradingOverrideService;
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

class QuizAttemptControllerTest {

  private final QuizAttemptSubmissionService submissions = mock(QuizAttemptSubmissionService.class);
  private final QuizAttemptResultService results = mock(QuizAttemptResultService.class);
  private final GradingOverrideService gradings = mock(GradingOverrideService.class);
  private final EssayAssessmentService essayAssessments = mock(EssayAssessmentService.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new QuizAttemptController(submissions, results, gradings, essayAssessments))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(accessPrincipalResolver())
            .build();
  }

  @Test
  void submitsWithClientAttemptIdUsingPutWithoutAnIdempotencyHeader() throws Exception {
    String attemptId = "550e8400-e29b-41d4-a716-446655440000";
    SubmittedQuizAttempt submitted =
        new SubmittedQuizAttempt(
            attemptId,
            QuizAttemptStatus.COMPLETED,
            new GradingCount(0, 1),
            List.of(),
            Instant.parse("2026-08-20T01:20:00Z"));
    when(submissions.submit(7L, "set_1", attemptId, List.of()))
        .thenReturn(new QuizAttemptSubmissionResult(true, submitted));

    mockMvc
        .perform(
            put("/api/v1/quiz-sets/set_1/attempts/{attemptId}", attemptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responses\":[]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.attemptId").value(attemptId));

    verify(submissions).submit(7L, "set_1", attemptId, List.of());
  }

  @Test
  void returnsOkWhenTheAttemptIdAlreadyExistsForTheSameQuizSet() throws Exception {
    String attemptId = "550e8400-e29b-41d4-a716-446655440000";
    SubmittedQuizAttempt submitted =
        new SubmittedQuizAttempt(
            attemptId,
            QuizAttemptStatus.COMPLETED,
            new GradingCount(0, 1),
            List.of(),
            Instant.parse("2026-08-20T01:20:00Z"));
    when(submissions.submit(7L, "set_1", attemptId, List.of()))
        .thenReturn(new QuizAttemptSubmissionResult(false, submitted));

    mockMvc
        .perform(
            put("/api/v1/quiz-sets/set_1/attempts/{attemptId}", attemptId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"responses\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.attemptId").value(attemptId));
  }

  @Test
  void acceptsAnOutcomeOnlyGradingUpdateAndReturnsTheWholeResult() throws Exception {
    QuizAttemptResult result = result(GradingOutcome.CORRECT, false);
    when(gradings.update(7L, "attempt_1", "question_2", "CORRECT")).thenReturn(result);

    mockMvc
        .perform(
            put("/api/v1/quiz-attempts/attempt_1/short-answer-gradings/question_2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"CORRECT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.attemptId").value("attempt_1"))
        .andExpect(jsonPath("$.data.questionResults[0].outcome").value("CORRECT"))
        .andExpect(jsonPath("$.data.summary.scoredGrading.correctQuestionCount").value(1))
        .andExpect(jsonPath("$.data.summary.reviewQuestionCount").value(0))
        .andExpect(jsonPath("$.data.gradingRevision").doesNotExist())
        .andExpect(jsonPath("$.data.summary.revision").doesNotExist());

    verify(gradings).update(7L, "attempt_1", "question_2", "CORRECT");
  }

  @Test
  void acceptsAGradingOverrideForAnAutomaticallyGradedQuestion() throws Exception {
    QuizAttemptResult result = result(GradingOutcome.CORRECT, false);
    when(gradings.update(7L, "attempt_1", "question_2", "CORRECT")).thenReturn(result);

    mockMvc
        .perform(
            put("/api/v1/quiz-attempts/attempt_1/grading-overrides/question_2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"CORRECT\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.attemptId").value("attempt_1"))
        .andExpect(jsonPath("$.data.questionResults[0].outcome").value("CORRECT"));

    verify(gradings).update(7L, "attempt_1", "question_2", "CORRECT");
  }

  @Test
  void resultProjectionKeepsOnlyTheCurrentShortAnswerOutcome() throws Exception {
    when(results.result(7L, "attempt_1")).thenReturn(result(GradingOutcome.CORRECT, false));

    mockMvc
        .perform(get("/api/v1/quiz-attempts/attempt_1/result"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.reviewAvailable").value(false))
        .andExpect(jsonPath("$.data.questionResults[0].response.answer").value("선입선출"))
        .andExpect(jsonPath("$.data.questionResults[0].outcome").value("CORRECT"))
        .andExpect(jsonPath("$.data.summary.revision").doesNotExist())
        .andExpect(jsonPath("$.data.questionResults[0].gradingRevision").doesNotExist())
        .andExpect(jsonPath("$.data.questionResults[0].automaticOutcome").doesNotExist())
        .andExpect(jsonPath("$.data.questionResults[0].correctedAt").doesNotExist())
        .andExpect(jsonPath("$.data.questionResults[0].unanswered").doesNotExist());
  }

  @Test
  void unansweredShortAnswerKeepsAnExplicitNullResponse() throws Exception {
    when(results.result(7L, "attempt_1")).thenReturn(result(GradingOutcome.INCORRECT, true));

    mockMvc
        .perform(get("/api/v1/quiz-attempts/attempt_1/result"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.questionResults[0].response").value((Object) null));
  }

  private QuizAttemptResult result(GradingOutcome outcome, boolean unanswered) {
    return new QuizAttemptResult(
        "attempt_1",
        "set_1",
        QuizAttemptStatus.COMPLETED,
        false,
        new QuizAttemptSummary(
            new GradingCount(outcome == GradingOutcome.CORRECT ? 1 : 0, 1),
            new EssaySelfAssessmentSummary(0, 0, 0),
            outcome == GradingOutcome.INCORRECT ? 1 : 0),
        List.of(
            new QuizQuestionResultView(
                "question_2",
                2,
                QuestionType.SHORT_ANSWER,
                "큐",
                "처리 순서는?",
                null,
                null,
                unanswered ? null : new AnswerValue("선입선출"),
                new AnswerValue("fifo"),
                outcome,
                "FIFO 구조",
                "FIFO 원칙")));
  }

  private HandlerMethodArgumentResolver accessPrincipalResolver() {
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
