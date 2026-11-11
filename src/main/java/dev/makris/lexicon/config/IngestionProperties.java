package dev.makris.lexicon.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "lexicon.ingestion")
public record IngestionProperties(
    @DefaultValue("1000") @Min(200) int chunkSize,
    @DefaultValue("150") @Min(0) int chunkOverlap,
    @DefaultValue("32") @Min(1) int embeddingBatchSize,
    @DefaultValue("768") @Min(1) int embeddingDimensions
) {}
