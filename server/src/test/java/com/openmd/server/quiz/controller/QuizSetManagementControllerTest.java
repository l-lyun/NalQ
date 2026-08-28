package com.openmd.server.quiz.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.error.GlobalExceptionHandler;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.dto.response.QuizSetListItem;
import com.openmd.server.quiz.dto.response.QuizSetPage;
import com.openmd.server.quiz.dto.response.RenamedQuizSet;
import com.openmd.server.quiz.dto.response.QuizSetView;
import com.openmd.server.quiz.service.QuizSetManagementService;
import com.openmd.server.quiz.service.QuizSetQueryService;
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

class QuizSetManagementControllerTest {
  private final QuizSetQueryService queries = mock(QuizSetQueryService.class);
  private final QuizSetManagementService management = mock(QuizSetManagementService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new QuizSetController(queries, management))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(principal())
            .build();
  }

  @Test
  void listsNavigationStateAndForwardsSearch() throws Exception {
    Instant activity = Instant.parse("2026-08-28T01:00:00Z");
    QuizSetListItem item =
        new QuizSetListItem(
            "set-1",
            "운영체제 퀴즈",
            "31",
            "운영체제",
            QuizSetStatus.READY,
            10,
            Instant.parse("2026-08-26T00:10:00Z"),
            Instant.parse("2026-08-27T00:10:00Z"),
            "main-complete",
            "main-pending",
            "review-active",
            2,
            activity);
    when(queries.list(7L, 1, 6, " 운영체제 "))
        .thenReturn(new QuizSetPage(List.of(item), 1, 6, 1, 1));

    mvc.perform(get("/api/v1/quiz-sets").param("query", " 운영체제 "))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].quizTitle").value("운영체제 퀴즈"))
        .andExpect(jsonPath("$.data.items[0].latestCompletedAttemptId").value("main-complete"))
        .andExpect(jsonPath("$.data.items[0].pendingSelfAssessmentAttemptId").value("main-pending"))
        .andExpect(jsonPath("$.data.items[0].activeReviewSessionId").value("review-active"))
        .andExpect(jsonPath("$.data.items[0].reviewQuestionCount").value(2))
        .andExpect(jsonPath("$.data.items[0].lastLearningActivityAt").value("2026-08-28T01:00:00Z"));
  }

  @Test
  void detailIncludesQuizTitleForEveryStatus() throws Exception {
    when(queries.get(7L, "set-1"))
        .thenReturn(
            new QuizSetView(
                "set-1",
                "31",
                "운영체제 퀴즈",
                QuizSetStatus.GENERATING,
                3,
                null,
                null));

    mvc.perform(get("/api/v1/quiz-sets/set-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quizTitle").value("운영체제 퀴즈"));
  }

  @Test
  void renamesAnOwnedQuizSet() throws Exception {
    Instant updatedAt = Instant.parse("2026-08-28T01:05:00Z");
    when(management.rename(7L, "set-1", " 새 이름 "))
        .thenReturn(new RenamedQuizSet("set-1", "새 이름", updatedAt));

    mvc.perform(
            patch("/api/v1/quiz-sets/set-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quizTitle\":\" 새 이름 \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quizTitle").value("새 이름"));
    verify(management).rename(7L, "set-1", " 새 이름 ");
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
