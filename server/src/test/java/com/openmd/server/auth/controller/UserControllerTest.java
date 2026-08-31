package com.openmd.server.auth.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.dto.response.CurrentUser;
import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.auth.service.AuthService;
import com.openmd.server.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class UserControllerTest {

	private final AuthService authService = mock(AuthService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new UserController(authService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.setCustomArgumentResolvers(accessPrincipalResolver())
			.build();
	}

	@Test
	void updatesTheAuthenticatedUsersNickname() throws Exception {
		when(authService.updateNickname(42L, "공부왕7")).thenReturn(new CurrentUser(
			42L, "learner@example.com", "공부왕7", true, UserStatus.ACTIVE
		));

		mockMvc.perform(patch("/api/v1/users/me")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"공부왕7\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.nickname").value("공부왕7"));

		verify(authService).updateNickname(42L, "공부왕7");
	}

	@Test
	void rejectsBlankNicknamesBeforeCallingTheService() throws Exception {
		mockMvc.perform(patch("/api/v1/users/me")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nickname\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_001"));

		verifyNoInteractions(authService);
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
				org.springframework.web.bind.support.WebDataBinderFactory binderFactory
			) {
				return new AccessPrincipal(42L, "session-id");
			}
		};
	}
}
