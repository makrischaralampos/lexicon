package dev.makris.lexicon.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "lexicon.retrieval")
public record RetrievalProperties(
    @DefaultValue("5") @Min(1) int defaultTopK,
    @DefaultValue("20") @Min(1) int maxTopK,
    @DefaultValue("30") @Min(1) int candidatePool,
    @DefaultValue("60") @Min(1) int rrfK
) {}
