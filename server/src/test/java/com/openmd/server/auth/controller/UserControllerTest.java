package com.openmd.server.auth.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.domain.UserStatus;
import com.openmd.server.auth.dto.response.CurrentUser;
import com.openmd.server.auth.dto.response.AccountWithdrawalResult;
import com.openmd.server.auth.controller.support.BrowserRefreshCookie;
import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.auth.service.AuthService;
import com.openmd.server.auth.service.AccountWithdrawalService;
import com.openmd.server.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class UserControllerTest {

	private final AuthService authService = mock(AuthService.class);
	private final AccountWithdrawalService withdrawalService = mock(AccountWithdrawalService.class);
	private final BrowserRefreshCookie refreshCookie = mock(BrowserRefreshCookie.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new UserController(authService, withdrawalService, refreshCookie))
			.setControllerAdvice(new GlobalExceptionHandler())
			.setCustomArgumentResolvers(accessPrincipalResolver())
			.build();
	}

	@Test
	void withdrawsTheAuthenticatedUserAndExpiresTheBrowserRefreshCookie() throws Exception {
		UUID requestId = UUID.fromString("018f5f95-61c7-7d7b-9f8c-6cb4a9b16731");
		when(withdrawalService.withdraw(42L, requestId.toString(), "password1", "회원탈퇴"))
			.thenReturn(new AccountWithdrawalResult(
				requestId,
				UserStatus.WITHDRAWN,
				Instant.parse("2026-09-03T06:00:00Z"),
				Instant.parse("2026-10-03T06:00:00Z")
			));
		when(refreshCookie.expire()).thenReturn(org.springframework.http.ResponseCookie.from(
			"openmd_refresh", ""
		).maxAge(0).build());

		mockMvc.perform(delete("/api/v1/users/me")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"withdrawalRequestId":"018f5f95-61c7-7d7b-9f8c-6cb4a9b16731",\
					 "currentPassword":"password1","confirmation":"회원탈퇴"}
					"""))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")))
			.andExpect(jsonPath("$.data.status").value("WITHDRAWN"))
			.andExpect(jsonPath("$.data.dataDisposalDeadline").value("2026-10-03T06:00:00Z"));

		verify(withdrawalService).withdraw(42L, requestId.toString(), "password1", "회원탈퇴");
	}

	@Test
	void rejectsMissingWithdrawalFieldsBeforeCallingTheService() throws Exception {
		mockMvc.perform(delete("/api/v1/users/me")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"withdrawalRequestId\":\"\",\"currentPassword\":\"\",\"confirmation\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_001"));

		verifyNoInteractions(withdrawalService);
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
