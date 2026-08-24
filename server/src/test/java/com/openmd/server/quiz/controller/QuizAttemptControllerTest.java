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
import com.openmd.server.quiz.domain.GradingOutcome;
import com.openmd.server.quiz.domain.QuestionType;
import com.openmd.server.quiz.domain.QuizAttemptStatus;
import com.openmd.server.quiz.dto.response.AnswerValue;
import com.openmd.server.quiz.dto.response.EssaySelfAssessmentSummary;
import com.openmd.server.quiz.dto.response.GradingCount;
import com.openmd.server.quiz.dto.response.QuizAttemptResult;
import com.openmd.server.quiz.dto.response.QuizAttemptSummary;
import com.openmd.server.quiz.dto.response.ShortAnswerGradingSummary;
import com.openmd.server.quiz.dto.response.ShortAnswerQuestionResult;
import com.openmd.server.quiz.dto.response.UpdatedShortAnswerGrading;
import com.openmd.server.quiz.service.QuizAttemptService;
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

	private final QuizAttemptService service = mock(QuizAttemptService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new QuizAttemptController(service))
			.setControllerAdvice(new GlobalExceptionHandler())
			.setCustomArgumentResolvers(accessPrincipalResolver())
			.build();
	}

	@Test
	void returnsOnlyTheFieldsNeededToReplaceTheChangedGradingAndSummary() throws Exception {
		when(service.updateShortAnswerGrading(7L, "attempt_1", "question_2", "key-1", "CORRECT", 0L))
			.thenReturn(new UpdatedShortAnswerGrading(
				"question_2", GradingOutcome.CORRECT, 1,
				new ShortAnswerGradingSummary(1, new GradingCount(3, 3), 1)
			));

		mockMvc.perform(put("/api/v1/quiz-attempts/attempt_1/short-answer-gradings/question_2")
				.header("Idempotency-Key", "key-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"outcome\":\"CORRECT\",\"expectedRevision\":0}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.questionId").value("question_2"))
			.andExpect(jsonPath("$.data.outcome").value("CORRECT"))
			.andExpect(jsonPath("$.data.gradingRevision").value(1))
			.andExpect(jsonPath("$.data.summary.revision").value(1))
			.andExpect(jsonPath("$.data.summary.scoredGrading.correctQuestionCount").value(3))
			.andExpect(jsonPath("$.data.summary.reviewQuestionCount").value(1))
			.andExpect(jsonPath("$.data.automaticOutcome").doesNotExist())
			.andExpect(jsonPath("$.data.response").doesNotExist())
			.andExpect(jsonPath("$.data.correctedAt").doesNotExist());

		verify(service).updateShortAnswerGrading(7L, "attempt_1", "question_2", "key-1", "CORRECT", 0L);
	}

	@Test
	void resultProjectionKeepsOnlyCurrentShortAnswerOutcomeAndRevision() throws Exception {
		when(service.result(7L, "attempt_1")).thenReturn(new QuizAttemptResult(
			"attempt_1", "set_1", QuizAttemptStatus.COMPLETED,
			new QuizAttemptSummary(1, new GradingCount(1, 1), new EssaySelfAssessmentSummary(0, 0, 0), 0),
			List.of(new ShortAnswerQuestionResult(
				"question_2", 2, QuestionType.SHORT_ANSWER, "큐", "처리 순서는?",
				new AnswerValue("선입선출"), new AnswerValue("fifo"), GradingOutcome.CORRECT, 1,
				"FIFO 구조", "FIFO 원칙"
			))
		));

		mockMvc.perform(get("/api/v1/quiz-attempts/attempt_1/result"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.questionResults[0].response.answer").value("선입선출"))
			.andExpect(jsonPath("$.data.questionResults[0].outcome").value("CORRECT"))
			.andExpect(jsonPath("$.data.questionResults[0].gradingRevision").value(1))
			.andExpect(jsonPath("$.data.questionResults[0].automaticOutcome").doesNotExist())
			.andExpect(jsonPath("$.data.questionResults[0].correctedAt").doesNotExist())
			.andExpect(jsonPath("$.data.questionResults[0].unanswered").doesNotExist());
	}

	@Test
	void unansweredShortAnswerKeepsAnExplicitNullResponse() throws Exception {
		when(service.result(7L, "attempt_1")).thenReturn(new QuizAttemptResult(
			"attempt_1", "set_1", QuizAttemptStatus.COMPLETED,
			new QuizAttemptSummary(0, new GradingCount(0, 1), new EssaySelfAssessmentSummary(0, 0, 0), 1),
			List.of(new ShortAnswerQuestionResult(
				"question_2", 2, QuestionType.SHORT_ANSWER, "큐", "처리 순서는?",
				null, new AnswerValue("fifo"), GradingOutcome.INCORRECT, 0,
				"FIFO 구조", "FIFO 원칙"
			))
		));

		mockMvc.perform(get("/api/v1/quiz-attempts/attempt_1/result"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.questionResults[0].response").value((Object) null));
	}

	private HandlerMethodArgumentResolver accessPrincipalResolver() {
		return new HandlerMethodArgumentResolver() {
			@Override public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType() == AccessPrincipal.class;
			}

			@Override public Object resolveArgument(
				MethodParameter parameter,
				ModelAndViewContainer container,
				NativeWebRequest request,
				org.springframework.web.bind.support.WebDataBinderFactory binderFactory
			) {
				return new AccessPrincipal(7L, "session");
			}
		};
	}
}
