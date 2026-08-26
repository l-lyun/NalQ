package com.openmd.server.notion.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.security.AccessPrincipal;
import com.openmd.server.notion.dto.response.NotionAuthorization;
import com.openmd.server.notion.dto.response.NotionConnectionView;
import com.openmd.server.notion.service.NotionConnectionService;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotionConnectionControllerTest {

	private final NotionConnectionService service = org.mockito.Mockito.mock(NotionConnectionService.class);
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new NotionConnectionController(service))
			.setControllerAdvice(new com.openmd.server.global.error.GlobalExceptionHandler())
			.setCustomArgumentResolvers(principalResolver())
			.build();
	}

	@Test
	void startsAndReadsAndDisconnectsTheAuthenticatedUsersConnection() throws Exception {
		when(service.startAuthorization(7L)).thenReturn(new NotionAuthorization("https://api.notion.com/authorize"));
		when(service.getConnection(7L)).thenReturn(NotionConnectionView.disconnected());

		mvc.perform(post("/api/v1/notion/authorizations"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.authorizationUrl").value("https://api.notion.com/authorize"));
		mvc.perform(get("/api/v1/notion/connection"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.connected").value(false));
		mvc.perform(delete("/api/v1/notion/connection"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true));

		verify(service).disconnect(7L);
	}

	@Test
	void callbackUsesOnlyCodeAndOneTimeStateThenRedirectsToTheConfiguredFrontend() throws Exception {
		when(service.completeAuthorization("code-1", "state-1"))
			.thenReturn(URI.create("http://localhost:5173/learning?notion=connected"));

		mvc.perform(get("/api/v1/notion/oauth/callback").param("code", "code-1").param("state", "state-1"))
			.andExpect(status().isFound())
			.andExpect(header().string("Location", "http://localhost:5173/learning?notion=connected"));
	}

	private HandlerMethodArgumentResolver principalResolver() {
		return new HandlerMethodArgumentResolver() {
			@Override
			public boolean supportsParameter(MethodParameter parameter) {
				return parameter.getParameterType() == AccessPrincipal.class;
			}

			@Override
			public Object resolveArgument(
				MethodParameter parameter,
				ModelAndViewContainer mavContainer,
				NativeWebRequest webRequest,
				org.springframework.web.bind.support.WebDataBinderFactory binderFactory
			) {
				return new AccessPrincipal(7L, "session");
			}
		};
	}
}
