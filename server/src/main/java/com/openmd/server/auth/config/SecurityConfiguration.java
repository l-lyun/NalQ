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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@ConditionalOnProperty(name = "openmd.auth.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfiguration {

	private static final String[] API_DOCUMENTATION_PATHS = {
		"/v3/api-docs/**",
		"/v3/api-docs.yaml",
		"/swagger-ui/**",
		"/swagger-ui.html"
	};

	@Bean
	SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		AccessTokenService tokens,
		ObjectMapper mapper,
		@Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled
	)
		throws Exception {
		BearerAccessTokenFilter bearerFilter = new BearerAccessTokenFilter(tokens, mapper);
		return http
			.cors(Customizer.withDefaults())
			.csrf(csrf -> csrf.ignoringRequestMatchers("/api/v1/auth/**"))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(authorize -> {
				authorize.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
				authorize.requestMatchers("/api/v1/auth/**").permitAll();
				if (apiDocsEnabled) {
					authorize.requestMatchers(API_DOCUMENTATION_PATHS).permitAll();
				}
				authorize.anyRequest().authenticated();
			})
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

	@Bean
	CorsConfigurationSource corsConfigurationSource(
		@Value("${openmd.cors.allowed-origins}") List<String> allowedOrigins
	) {
		return buildCorsConfigurationSource(allowedOrigins);
	}

	static CorsConfigurationSource buildCorsConfigurationSource(List<String> allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.copyOf(allowedOrigins));
		configuration.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
