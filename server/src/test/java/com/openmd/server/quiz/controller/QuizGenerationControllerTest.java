package com.openmd.server.quiz.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.global.error.GlobalExceptionHandler;
import com.openmd.server.quiz.domain.type.QuestionType;
import com.openmd.server.quiz.domain.type.QuizDifficulty;
import com.openmd.server.quiz.domain.type.QuizSetStatus;
import com.openmd.server.quiz.dto.command.QuizGenerationConfig;
import com.openmd.server.quiz.dto.response.AcceptedQuizGeneration;
import com.openmd.server.quiz.dto.response.ActiveQuizGeneration;
import com.openmd.server.quiz.dto.response.RequestedQuizConfig;
import com.openmd.server.quiz.service.QuizGenerationService;
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

class QuizGenerationControllerTest {
  private final QuizGenerationService service = mock(QuizGenerationService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new QuizGenerationController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(principal())
            .build();
  }

  @Test
  void acceptsGenerationAndEchoesRequestedConfigWithoutReturningTheIdempotencyKey()
      throws Exception {
    RequestedQuizConfig config =
        new RequestedQuizConfig(
            List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.ESSAY), QuizDifficulty.NORMAL, 10);
    QuizGenerationConfig command =
        new QuizGenerationConfig(
            config.selectedTypes(), config.difficulty(), config.maxQuestionCount());
    when(service.accept(7L, "123", command))
        .thenReturn(
            new AcceptedQuizGeneration(
                "set-1",
                "123",
                QuizSetStatus.GENERATING,
                3,
                config,
                Instant.parse("2026-08-20T01:05:00Z")));

    mvc.perform(
            post("/api/v1/learning-materials/123/quiz-sets")
                .header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"selectedTypes":["MULTIPLE_CHOICE","ESSAY"],
                     "difficulty":"NORMAL","maxQuestionCount":10}
                    """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.data.quizSetId").value("set-1"))
        .andExpect(jsonPath("$.data.requestedConfig.selectedTypes[1]").value("ESSAY"))
        .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist());
    verify(service).accept(7L, "123", command);
  }

  @Test
  void returnsTheActiveGenerationOrAnExplicitNull() throws Exception {
    when(service.active(7L, "123"))
        .thenReturn(new ActiveQuizGeneration("set-1", "123", QuizSetStatus.GENERATING, 3));
    mvc.perform(get("/api/v1/learning-materials/123/quiz-sets/active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.quizSetId").value("set-1"))
        .andExpect(jsonPath("$.data.requestedConfig").doesNotExist());

    when(service.active(7L, "123")).thenReturn(null);
    mvc.perform(get("/api/v1/learning-materials/123/quiz-sets/active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value((Object) null));
  }

  @Test
  void reportsUnknownGenerationEnumsAsInvalidFields() throws Exception {
    mvc.perform(
            post("/api/v1/learning-materials/123/quiz-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"selectedTypes":["UNKNOWN"],"difficulty":"IMPOSSIBLE","maxQuestionCount":10}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("COMMON_001"))
        .andExpect(jsonPath("$.error.fields[0].field").value("selectedTypes"))
        .andExpect(jsonPath("$.error.fields[1].field").value("difficulty"));
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
