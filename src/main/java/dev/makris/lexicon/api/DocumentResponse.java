package dev.makris.lexicon.api;

import dev.makris.lexicon.document.DocumentStatus;
import dev.makris.lexicon.document.StoredDocument;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    String filename,
    String contentType,
    long sizeBytes,
    DocumentStatus status,
    String failureReason,
    Integer pageCount,
    int chunkCount,
    Instant createdAt
) {
    public static DocumentResponse from(StoredDocument d) {
        return new DocumentResponse(
            d.id(),
            d.filename(),
            d.contentType(),
            d.sizeBytes(),
            d.status(),
            d.failureReason(),
            d.pageCount(),
            d.chunkCount(),
            d.createdAt()
        );
    }
}
