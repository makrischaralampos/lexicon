package dev.makris.lexicon.chunk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkRepository {

    private static final String INSERT = """
    INSERT INTO chunks (document_id, chunk_index, page_number, content, embedding)
    VALUES (?, ?, ?, ?, ?::vector)
    """;

    private final JdbcTemplate jdbcTemplate;

    public ChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAll(UUID documentId, List<NewChunk> chunks) {
        List<Object[]> batch = chunks
            .stream()
            .map(c -> new Object[] {
                documentId,
                c.index(),
                c.pageNumber(),
                c.content(),
                PgVector.toLiteral(c.embedding()),
            })
            .toList();
        jdbcTemplate.batchUpdate(INSERT, batch);
    }

    public Optional<String> findContent(UUID chunkId) {
        return jdbcTemplate
            .queryForList(
                "SELECT content FROM chunks WHERE id = ?",
                String.class,
                chunkId
            )
            .stream()
            .findFirst();
    }
}
