package com.baracel.portal.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {
    static {
        org.springdoc.core.utils.SpringDocUtils.getConfig().addRequestWrapperToIgnore(com.baracel.portal.common.security.CurrentUser.class);
    }

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    OpenAPI portalOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Baracel Employee Portal API").version("0.1.0")
                        .description("All endpoints need a Keycloak JWT (audience portal-api), except /actuator/health and the Gitea webhook."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
