package dev.makris.lexicon.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI lexiconOpenApi() {
        return new OpenAPI()
            .info(
                new Info()
                    .title("Lexicon API")
                    .version("v1")
                    .description(
                        "Document-search API with cited answers, running on a local LLM."
                    )
            )
            .components(
                new Components().addSecuritySchemes(
                    "apiKey",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(ApiKeyFilter.HEADER)
                )
            )
            .addSecurityItem(new SecurityRequirement().addList("apiKey"));
    }
}
