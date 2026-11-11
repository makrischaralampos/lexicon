package dev.makris.lexicon.api;

import dev.makris.lexicon.retrieval.RetrievedChunk;
import dev.makris.lexicon.retrieval.SearchMode;
import java.util.List;
import java.util.UUID;

public record SearchResponse(
    String query,
    SearchMode mode,
    int topK,
    long tookMs,
    List<Hit> results
) {
    public record Hit(
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
        static Hit from(RetrievedChunk c) {
            return new Hit(
                c.chunkId(),
                c.documentId(),
                c.filename(),
                c.pageNumber(),
                c.chunkIndex(),
                c.content(),
                c.score(),
                c.similarity(),
                c.vectorRank(),
                c.keywordRank()
            );
        }
    }

    public static SearchResponse of(
        String query,
        SearchMode mode,
        int topK,
        long tookMs,
        List<RetrievedChunk> chunks
    ) {
        return new SearchResponse(
            query,
            mode,
            topK,
            tookMs,
            chunks.stream().map(Hit::from).toList()
        );
    }
}
