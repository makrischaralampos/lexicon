package dev.makris.lexicon.document;

import java.time.Instant;
import java.util.UUID;

public record StoredDocument(
    UUID id,
    String filename,
    String contentType,
    String sha256,
    long sizeBytes,
    DocumentStatus status,
    String failureReason,
    Integer pageCount,
    int chunkCount,
    Instant createdAt,
    Instant updatedAt
) {}
