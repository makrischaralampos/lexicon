package dev.makris.lexicon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional text prefixes added before embedding. Some embedding models (e.g. nomic-embed-text) are trained
 * with task prefixes such as "search_query: " and "search_document: ". Empty means no prefix.
 */
@ConfigurationProperties(prefix = "lexicon.embedding")
public record EmbeddingProperties(
    @DefaultValue("") String queryPrefix,
    @DefaultValue("") String documentPrefix
) {}
