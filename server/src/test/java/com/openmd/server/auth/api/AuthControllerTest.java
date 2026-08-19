package com.openmd.server.auth.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openmd.server.auth.application.AuthService;
import com.openmd.server.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

	private final AuthService authService = mock(AuthService.class);
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void acceptsSignUpWithoutReturningSensitiveData() throws Exception {
		mockMvc.perform(post("/api/v1/auth/sign-ups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"learner@example.com","password":"password1"}
					"""))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.verificationRequired").value(true))
			.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	void rejectsPasswordsOutsideTheConfirmedPolicyBeforeCallingTheService() throws Exception {
		mockMvc.perform(post("/api/v1/auth/sign-ups")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"learner@example.com","password":"onlyletters"}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.error.code").value("COMMON_001"))
			.andExpect(jsonPath("$.error.fields[0].field").value("password"));
		verifyNoInteractions(authService);
	}

	@Test
	void rejectsOversizedCredentialsBeforeHashingOrParsingThem() throws Exception {
		mockMvc.perform(post("/api/v1/auth/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"learner@example.com\",\"password\":\"" + "a".repeat(65) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_001"));

		mockMvc.perform(post("/api/v1/auth/email-verifications/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"learner@example.com\",\"code\":\"" + "A".repeat(65) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_001"));

		mockMvc.perform(post("/api/v1/auth/sessions/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\":\"" + "A".repeat(129) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("COMMON_001"));

		verifyNoInteractions(authService);
	}
}
