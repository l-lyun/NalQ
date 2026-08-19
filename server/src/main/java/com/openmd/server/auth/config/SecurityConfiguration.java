package com.openmd.server.auth.config;

import tools.jackson.databind.ObjectMapper;
import com.openmd.server.auth.domain.AuthErrorCode;
import com.openmd.server.auth.security.AccessTokenService;
import com.openmd.server.auth.security.BearerAccessTokenFilter;
import com.openmd.server.global.api.ApiError;
import com.openmd.server.global.api.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@ConditionalOnProperty(name = "openmd.auth.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfiguration {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokenService tokens, ObjectMapper mapper)
		throws Exception {
		BearerAccessTokenFilter bearerFilter = new BearerAccessTokenFilter(tokens, mapper);
		return http
			.csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/auth/**"))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers("/api/v1/auth/**").permitAll()
				.anyRequest().authenticated()
			)
			.exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
				response.setStatus(401);
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.setCharacterEncoding("UTF-8");
				mapper.writeValue(response.getOutputStream(), ApiResponse.failure(ApiError.of(
					AuthErrorCode.INVALID_CREDENTIAL.code(), AuthErrorCode.INVALID_CREDENTIAL.message()
				)));
			}))
			.addFilterBefore(bearerFilter, UsernamePasswordAuthenticationFilter.class)
			.build();
	}
}
