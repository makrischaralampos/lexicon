package dev.makris.lexicon.retrieval;

import dev.makris.lexicon.common.ApiException;
import dev.makris.lexicon.config.EmbeddingProperties;
import dev.makris.lexicon.config.RetrievalProperties;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(
        RetrievalService.class
    );

    private final ChunkSearchRepository repository;
    private final EmbeddingModel embeddingModel;
    private final RetrievalProperties properties;
    private final EmbeddingProperties embeddingProperties;

    public RetrievalService(
        ChunkSearchRepository repository,
        EmbeddingModel embeddingModel,
        RetrievalProperties properties,
        EmbeddingProperties embeddingProperties
    ) {
        this.repository = repository;
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.embeddingProperties = embeddingProperties;
    }

    /** The requested topK (or the default), capped at the configured maximum. */
    public int effectiveTopK(Integer requested) {
        int topK = requested == null ? properties.defaultTopK() : requested;
        return Math.max(1, Math.min(topK, properties.maxTopK()));
    }

    public List<RetrievedChunk> retrieve(
        String query,
        SearchMode mode,
        int topK,
        List<UUID> documentIds
    ) {
        float[] embedding = embedQuery(query);
        String tsQuery = KeywordQuery.toTsQuery(query);

        return switch (mode) {
            case VECTOR -> repository.vectorSearch(
                embedding,
                topK,
                documentIds
            );
            case KEYWORD -> tsQuery.isEmpty()
                ? List.of()
                : repository.keywordSearch(
                      embedding,
                      tsQuery,
                      topK,
                      documentIds
                  );
            case HYBRID -> repository.hybridSearch(
                embedding,
                tsQuery,
                topK,
                Math.max(properties.candidatePool(), topK),
                properties.rrfK(),
                documentIds
            );
        };
    }

    private float[] embedQuery(String query) {
        try {
            return embeddingModel.embed(
                embeddingProperties.queryPrefix() + query
            );
        } catch (RuntimeException e) {
            log.error("Query embedding failed", e);
            throw ApiException.serviceUnavailable(
                "The embedding model is unavailable. Is Ollama running?"
            );
        }
    }
}
