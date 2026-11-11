package dev.makris.lexicon.document;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private static final RowMapper<StoredDocument> MAPPER = (rs, rowNum) ->
        new StoredDocument(
            rs.getObject("id", UUID.class),
            rs.getString("filename"),
            rs.getString("content_type"),
            rs.getString("sha256"),
            rs.getLong("size_bytes"),
            DocumentStatus.valueOf(rs.getString("status")),
            rs.getString("failure_reason"),
            rs.getObject("page_count", Integer.class),
            rs.getInt("chunk_count"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );

    private final JdbcClient jdbc;

    public DocumentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts a PROCESSING row; returns empty if a document with the same hash already exists. */
    public Optional<UUID> insertIfAbsent(
        String filename,
        String contentType,
        String sha256,
        long sizeBytes
    ) {
        return jdbc
            .sql(
                """
                INSERT INTO documents (filename, content_type, sha256, size_bytes, status)
                VALUES (:filename, :contentType, :sha256, :sizeBytes, 'PROCESSING')
                ON CONFLICT (sha256) DO NOTHING
                RETURNING id
                """
            )
            .param("filename", filename)
            .param("contentType", contentType)
            .param("sha256", sha256)
            .param("sizeBytes", sizeBytes)
            .query(UUID.class)
            .optional();
    }

    public Optional<StoredDocument> findById(UUID id) {
        return jdbc
            .sql("SELECT * FROM documents WHERE id = :id")
            .param("id", id)
            .query(MAPPER)
            .optional();
    }

    public Optional<StoredDocument> findBySha256(String sha256) {
        return jdbc
            .sql("SELECT * FROM documents WHERE sha256 = :sha256")
            .param("sha256", sha256)
            .query(MAPPER)
            .optional();
    }

    public List<StoredDocument> findAll(int limit, int offset) {
        return jdbc
            .sql(
                "SELECT * FROM documents ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset"
            )
            .param("limit", limit)
            .param("offset", offset)
            .query(MAPPER)
            .list();
    }

    public long count() {
        return jdbc
            .sql("SELECT count(*) FROM documents")
            .query(Long.class)
            .single();
    }

    public void markReady(UUID id, Integer pageCount, int chunkCount) {
        jdbc.sql(
            """
            UPDATE documents
            SET status = 'READY', page_count = :pageCount, chunk_count = :chunkCount,
                failure_reason = NULL, updated_at = now()
            WHERE id = :id
            """
        )
            .param("id", id)
            .param("pageCount", pageCount)
            .param("chunkCount", chunkCount)
            .update();
    }

    public void markFailed(UUID id, String reason) {
        jdbc.sql(
            """
            UPDATE documents
            SET status = 'FAILED', failure_reason = :reason, updated_at = now()
            WHERE id = :id
            """
        )
            .param("id", id)
            .param("reason", reason)
            .update();
    }

    public boolean delete(UUID id) {
        return (
            jdbc
                .sql("DELETE FROM documents WHERE id = :id")
                .param("id", id)
                .update() > 0
        );
    }
}
