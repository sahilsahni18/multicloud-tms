package com.trackflow.tms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 3 description served at /v3/api-docs and rendered at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI trackflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TrackFlow API")
                        .version("v1")
                        .description("""
                                Multi-Cloud Ticket Management System.
                                Log in with POST /api/v1/auth/login, then click Authorize and paste the accessToken.
                                Errors use RFC 7807 application/problem+json.""")
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
