package com.chubb.claims.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI claimsOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Chubb APAC Claims API")
                .version("v1")
                .description("Submit, track and manage claims. Errors return a consistent JSON response "
                        + "with timestamp, HTTP status, code, message and path. "
                        + "Demo only: staff endpoints are not yet authenticated."));
    }
}
