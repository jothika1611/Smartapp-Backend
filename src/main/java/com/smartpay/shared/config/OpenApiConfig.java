package com.smartpay.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger metadata. Every endpoint must state in its description that data is simulated. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartPayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartPay API - SIMULATED")
                        .version("v1")
                        .description("Portfolio demonstration API. No real money, bank, card or gateway is "
                                + "involved. All balances and transactions are simulated."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the access token returned by POST /api/v1/auth/login.")));
    }
}
