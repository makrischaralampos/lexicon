package dev.makris.lexicon.answer;

import dev.makris.lexicon.retrieval.RetrievedChunk;
import java.util.UUID;

public record Citation(
    int index,
    UUID chunkId,
    UUID documentId,
    String filename,
    Integer pageNumber,
    String snippet
) {
    private static final int SNIPPET_LENGTH = 300;

    static Citation of(int index, RetrievedChunk chunk) {
        String flat = chunk.content().replaceAll("\\s+", " ").strip();
        String snippet =
            flat.length() > SNIPPET_LENGTH
                ? flat.substring(0, SNIPPET_LENGTH) + "…"
                : flat;
        return new Citation(
            index,
            chunk.chunkId(),
            chunk.documentId(),
            chunk.filename(),
            chunk.pageNumber(),
            snippet
        );
    }
}
