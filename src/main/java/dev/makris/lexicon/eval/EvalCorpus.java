package dev.makris.lexicon.eval;

import dev.makris.lexicon.document.StoredDocument;
import dev.makris.lexicon.ingestion.IngestionService;
import dev.makris.lexicon.ingestion.SupportedType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Ingests the evaluation corpus (idempotently) and checks the dataset's gold labels against it. */
@Component
@Profile("eval")
class EvalCorpus {

    private static final Logger log = LoggerFactory.getLogger(EvalCorpus.class);

    record Loaded(List<UUID> documentIds, int documentCount, int chunkCount) {}

    private final EvalProperties properties;
    private final IngestionService ingestion;
    private final JdbcClient jdbc;

    EvalCorpus(
        EvalProperties properties,
        IngestionService ingestion,
        JdbcClient jdbc
    ) {
        this.properties = properties;
        this.ingestion = ingestion;
        this.jdbc = jdbc;
    }

    Loaded load() throws IOException {
        Path dir = Path.of(properties.corpusDir());
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream
                .filter(Files::isRegularFile)
                .filter(p ->
                    SupportedType.fromFilename(
                        p.getFileName().toString()
                    ).isPresent()
                )
                .sorted()
                .toList();
        }
        if (files.isEmpty()) {
            throw new IllegalStateException(
                "No supported documents found in " + dir.toAbsolutePath()
            );
        }

        Map<String, StoredDocument> byName = new LinkedHashMap<>();
        for (Path file : files) {
            StoredDocument document = ingestion
                .ingest(file.getFileName().toString(), Files.readAllBytes(file))
                .document();
            log.info(
                "Corpus document '{}' ({} chunks)",
                document.filename(),
                document.chunkCount()
            );
            byName.put(document.filename(), document);
        }

        lint(byName);

        return new Loaded(
            byName.values().stream().map(StoredDocument::id).toList(),
            byName.size(),
            byName.values().stream().mapToInt(StoredDocument::chunkCount).sum()
        );
    }

    /** Fails fast when a gold label can never match, instead of silently skewing the metrics. */
    private void lint(Map<String, StoredDocument> corpus) {
        List<String> problems = new ArrayList<>();
        for (EvalQuestion q : properties.questions()) {
            if (!q.answerable()) {
                continue;
            }
            StoredDocument document = corpus.get(q.expectedDocument());
            if (document == null) {
                problems.add(
                    q.id() +
                        ": expected document '" +
                        q.expectedDocument() +
                        "' is not in the corpus"
                );
                continue;
            }
            boolean found = q
                .evidence()
                .stream()
                .anyMatch(e -> evidenceExists(document.id(), e));
            if (!found) {
                problems.add(
                    q.id() +
                        ": no evidence string appears inside a single chunk of " +
                        document.filename()
                );
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                "Dataset problems:\n" + String.join("\n", problems)
            );
        }
    }

    private boolean evidenceExists(UUID documentId, String evidence) {
        Long count = jdbc
            .sql(
                """
                SELECT count(*) FROM chunks
                WHERE document_id = :doc
                  AND position(CAST(:needle AS text) IN regexp_replace(lower(content), '\\s+', ' ', 'g')) > 0
                """
            )
            .param("doc", documentId)
            .param("needle", TextMatch.normalize(evidence))
            .query(Long.class)
            .single();
        return count != null && count > 0;
    }
}
