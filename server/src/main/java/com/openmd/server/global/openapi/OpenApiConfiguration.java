package com.openmd.server.global.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfiguration {

	public static final String BEARER_AUTH = "bearerAuth";
	public static final String BROWSER_REFRESH_COOKIE = "browserRefreshCookie";
	private static final String PUBLIC_AUTH_PATH_PREFIX = "/api/v1/auth/";
	private static final String BROWSER_REFRESH_PATH = "/api/v1/auth/web/sessions/refresh";
	private static final String BROWSER_LOGOUT_PATH = "/api/v1/auth/web/sessions/current";

	@Bean
	OpenAPI openMdOpenApi(
		@Value("${openmd.auth.browser.cookie.name}") String browserRefreshCookieName
	) {
		SecurityScheme bearerAuth = new SecurityScheme()
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT");
		SecurityScheme browserRefreshCookie = new SecurityScheme()
			.type(SecurityScheme.Type.APIKEY)
			.in(SecurityScheme.In.COOKIE)
			.name(browserRefreshCookieName);

		return new OpenAPI()
			.info(new Info()
				.title("NalQ API")
				.version("v1")
				.description("NalQ 서버의 실행 가능한 HTTP 계약입니다."))
			.components(new Components()
				.addSecuritySchemes(BEARER_AUTH, bearerAuth)
				.addSecuritySchemes(BROWSER_REFRESH_COOKIE, browserRefreshCookie))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

	@Bean
	OpenApiCustomizer publicAuthenticationOperations() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().forEach((path, pathItem) -> {
				if (BROWSER_REFRESH_PATH.equals(path) || BROWSER_LOGOUT_PATH.equals(path)) {
					pathItem.readOperations().forEach(operation -> operation.setSecurity(
						List.of(new SecurityRequirement().addList(BROWSER_REFRESH_COOKIE))
					));
				} else if (path.startsWith(PUBLIC_AUTH_PATH_PREFIX)) {
					pathItem.readOperations().forEach(operation -> operation.setSecurity(List.of()));
				}
			});
		};
	}
}
