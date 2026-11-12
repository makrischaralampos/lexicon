package dev.makris.lexicon.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "lexicon.answer")
public record AnswerProperties(
    @DefaultValue("0.40")
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    double minSimilarity,
    @DefaultValue("8000") @Min(1000) int maxContextChars
) {}
