package com.openmd.server.global.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfiguration {

	public static final String BEARER_AUTH = "bearerAuth";
	private static final String PUBLIC_AUTH_PATH_PREFIX = "/api/v1/auth/";

	@Bean
	OpenAPI openMdOpenApi() {
		SecurityScheme bearerAuth = new SecurityScheme()
			.type(SecurityScheme.Type.HTTP)
			.scheme("bearer")
			.bearerFormat("JWT");

		return new OpenAPI()
			.info(new Info()
				.title("OpenMD API")
				.version("v1")
				.description("OpenMD 서버의 실행 가능한 HTTP 계약입니다."))
			.components(new Components().addSecuritySchemes(BEARER_AUTH, bearerAuth))
			.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

	@Bean
	OpenApiCustomizer publicAuthenticationOperations() {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().forEach((path, pathItem) -> {
				if (path.startsWith(PUBLIC_AUTH_PATH_PREFIX)) {
					pathItem.readOperations().forEach(operation -> operation.setSecurity(List.of()));
				}
			});
		};
	}
}
