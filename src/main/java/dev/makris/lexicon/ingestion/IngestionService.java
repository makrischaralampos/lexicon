package dev.makris.lexicon.ingestion;

import dev.makris.lexicon.chunk.ChunkRepository;
import dev.makris.lexicon.chunk.NewChunk;
import dev.makris.lexicon.common.ApiException;
import dev.makris.lexicon.config.IngestionProperties;
import dev.makris.lexicon.document.DocumentRepository;
import dev.makris.lexicon.document.DocumentStatus;
import dev.makris.lexicon.document.StoredDocument;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(
        IngestionService.class
    );

    public record IngestionResult(StoredDocument document, boolean created) {}

    private record PendingChunk(int index, Integer pageNumber, String text) {}

    private final DocumentRepository documents;
    private final ChunkRepository chunks;
    private final DocumentParser parser;
    private final EmbeddingModel embeddingModel;
    private final TransactionTemplate transactions;
    private final IngestionProperties properties;
    private final TextChunker chunker;

    public IngestionService(
        DocumentRepository documents,
        ChunkRepository chunks,
        DocumentParser parser,
        EmbeddingModel embeddingModel,
        TransactionTemplate transactions,
        IngestionProperties properties
    ) {
        this.documents = documents;
        this.chunks = chunks;
        this.parser = parser;
        this.embeddingModel = embeddingModel;
        this.transactions = transactions;
        this.properties = properties;
        this.chunker = new TextChunker(
            properties.chunkSize(),
            properties.chunkOverlap()
        );
    }

    public IngestionResult ingest(String filename, byte[] content) {
        if (content.length == 0) {
            throw ApiException.unprocessable("The uploaded file is empty.");
        }
        SupportedType type = SupportedType.fromFilename(filename).orElseThrow(
            () ->
                ApiException.unsupportedMediaType(
                    "Unsupported file type. Supported: .pdf, .docx, .md, .markdown, .txt"
                )
        );

        String sha256 = sha256Hex(content);
        Optional<UUID> inserted = documents.insertIfAbsent(
            filename,
            type.contentType(),
            sha256,
            content.length
        );

        if (inserted.isEmpty()) {
            StoredDocument existing = documents
                .findBySha256(sha256)
                .orElseThrow();
            if (existing.status() != DocumentStatus.FAILED) {
                return new IngestionResult(existing, false);
            }
            // A previous attempt failed: discard it and retry from scratch.
            documents.delete(existing.id());
            return ingest(filename, content);
        }

        UUID id = inserted.get();
        try {
            process(id, type, filename, content);
        } catch (ApiException e) {
            documents.markFailed(id, e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            log.error("Ingestion of '{}' failed", filename, e);
            documents.markFailed(
                id,
                "Unexpected error while processing the document."
            );
            throw e;
        }

        return new IngestionResult(documents.findById(id).orElseThrow(), true);
    }

    private void process(
        UUID id,
        SupportedType type,
        String filename,
        byte[] content
    ) {
        long startedAt = System.nanoTime();

        List<DocumentParser.PageText> pages = parser.parse(
            type,
            filename,
            content
        );

        List<PendingChunk> pending = new ArrayList<>();
        int index = 0;
        for (DocumentParser.PageText page : pages) {
            for (String piece : chunker.split(page.text())) {
                pending.add(
                    new PendingChunk(index++, page.pageNumber(), piece)
                );
            }
        }
        if (pending.isEmpty()) {
            throw ApiException.unprocessable(
                "No extractable text was found (scanned PDFs need OCR, which is not supported)."
            );
        }

        List<float[]> embeddings = embedAll(
            pending.stream().map(PendingChunk::text).toList()
        );

        List<NewChunk> rows = IntStream.range(0, pending.size())
            .mapToObj(i ->
                new NewChunk(
                    pending.get(i).index(),
                    pending.get(i).pageNumber(),
                    pending.get(i).text(),
                    embeddings.get(i)
                )
            )
            .toList();
        Integer pageCount = type == SupportedType.PDF ? pages.size() : null;

        transactions.executeWithoutResult(status -> {
            chunks.insertAll(id, rows);
            documents.markReady(id, pageCount, rows.size());
        });

        log.info(
            "Ingested '{}' ({} chunks) in {} ms",
            filename,
            rows.size(),
            (System.nanoTime() - startedAt) / 1_000_000
        );
    }

    private List<float[]> embedAll(List<String> texts) {
        int batchSize = properties.embeddingBatchSize();
        List<float[]> result = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += batchSize) {
            List<String> batch = texts.subList(
                from,
                Math.min(from + batchSize, texts.size())
            );
            List<float[]> vectors;
            try {
                vectors = embeddingModel.embed(batch);
            } catch (RuntimeException e) {
                log.error("Embedding request failed", e);
                throw ApiException.serviceUnavailable(
                    "The embedding model is unavailable. Is Ollama running?"
                );
            }
            for (float[] vector : vectors) {
                if (vector.length != properties.embeddingDimensions()) {
                    throw ApiException.unprocessable(
                        "Embedding model returned " +
                            vector.length +
                            " dimensions but the database schema expects " +
                            properties.embeddingDimensions() +
                            "."
                    );
                }
            }
            result.addAll(vectors);
        }
        return result;
    }

    private static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
