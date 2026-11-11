package dev.makris.lexicon.retrieval;

import java.util.UUID;

/**
 * A chunk returned by retrieval. {@code score} is mode-specific (cosine similarity, ts_rank_cd or RRF),
 * {@code similarity} is always the cosine similarity to the query, and the ranks are 1-based positions
 * in each retrieval leg (null when the chunk was not found by that leg).
 */
public record RetrievedChunk(
    UUID chunkId,
    UUID documentId,
    String filename,
    Integer pageNumber,
    int chunkIndex,
    String content,
    double score,
    double similarity,
    Integer vectorRank,
    Integer keywordRank
) {
    public RetrievedChunk withVectorRank(int rank) {
        return new RetrievedChunk(
            chunkId,
            documentId,
            filename,
            pageNumber,
            chunkIndex,
            content,
            score,
            similarity,
            rank,
            keywordRank
        );
    }

    public RetrievedChunk withKeywordRank(int rank) {
        return new RetrievedChunk(
            chunkId,
            documentId,
            filename,
            pageNumber,
            chunkIndex,
            content,
            score,
            similarity,
            vectorRank,
            rank
        );
    }
}
