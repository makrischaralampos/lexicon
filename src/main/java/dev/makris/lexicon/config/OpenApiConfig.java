package dev.makris.lexicon.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI lexiconOpenApi() {
        return new OpenAPI().info(
            new Info()
                .title("Lexicon API")
                .version("v1")
                .description(
                    "Document-search API with cited answers, running on a local LLM."
                )
        );
    }
}
