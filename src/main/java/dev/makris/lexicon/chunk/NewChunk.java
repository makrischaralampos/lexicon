package dev.makris.lexicon.chunk;

public record NewChunk(
    int index,
    Integer pageNumber,
    String content,
    float[] embedding
) {}
