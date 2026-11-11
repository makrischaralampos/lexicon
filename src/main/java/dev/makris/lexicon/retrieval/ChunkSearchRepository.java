package dev.makris.lexicon.retrieval;

import dev.makris.lexicon.chunk.PgVector;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ChunkSearchRepository {

    private static final String DOCUMENT_FILTER =
        "AND c.document_id = ANY(CAST(:documentIds AS uuid[]))";

    private static final RowMapper<RetrievedChunk> MAPPER = (rs, rowNum) ->
        new RetrievedChunk(
            rs.getObject("id", UUID.class),
            rs.getObject("document_id", UUID.class),
            rs.getString("filename"),
            rs.getObject("page_number", Integer.class),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getDouble("score"),
            rs.getDouble("similarity"),
            nullableInt(rs, "vector_rank"),
            nullableInt(rs, "keyword_rank")
        );

    private static final String VECTOR_SQL = """
    SELECT c.id, c.document_id, d.filename, c.page_number, c.chunk_index, c.content,
           1 - (c.embedding <=> CAST(:embedding AS vector)) AS similarity,
           1 - (c.embedding <=> CAST(:embedding AS vector)) AS score,
           CAST(NULL AS integer) AS vector_rank,
           CAST(NULL AS integer) AS keyword_rank
    FROM chunks c
    JOIN documents d ON d.id = c.document_id
    WHERE TRUE {{filter}}
    ORDER BY c.embedding <=> CAST(:embedding AS vector)
    LIMIT :limit
    """;

    private static final String KEYWORD_SQL = """
    WITH q AS (SELECT to_tsquery('english', :tsquery) AS query)
    SELECT c.id, c.document_id, d.filename, c.page_number, c.chunk_index, c.content,
           1 - (c.embedding <=> CAST(:embedding AS vector)) AS similarity,
           ts_rank_cd(c.content_tsv, q.query) AS score,
           CAST(NULL AS integer) AS vector_rank,
           CAST(NULL AS integer) AS keyword_rank
    FROM chunks c
    JOIN documents d ON d.id = c.document_id
    CROSS JOIN q
    WHERE c.content_tsv @@ q.query {{filter}}
    ORDER BY score DESC, c.id
    LIMIT :limit
    """;

    /**
     * Reciprocal Rank Fusion: each leg contributes 1 / (k + rank). The inner ORDER BY ... LIMIT of the
     * vector leg is what lets PostgreSQL use the HNSW index.
     */
    private static final String HYBRID_SQL = """
    WITH q AS (SELECT to_tsquery('english', :tsquery) AS query),
    vector_hits AS (
        SELECT id, ROW_NUMBER() OVER (ORDER BY dist) AS pos
        FROM (
            SELECT c.id, c.embedding <=> CAST(:embedding AS vector) AS dist
            FROM chunks c
            WHERE TRUE {{filter}}
            ORDER BY c.embedding <=> CAST(:embedding AS vector)
            LIMIT :pool
        ) v
    ),
    keyword_hits AS (
        SELECT id, ROW_NUMBER() OVER (ORDER BY score DESC, id) AS pos
        FROM (
            SELECT c.id, ts_rank_cd(c.content_tsv, q.query) AS score
            FROM chunks c, q
            WHERE c.content_tsv @@ q.query {{filter}}
            ORDER BY score DESC, c.id
            LIMIT :pool
        ) k
    ),
    fused AS (
        SELECT COALESCE(v.id, k.id) AS id,
               COALESCE(1.0 / (:rrfK + v.pos), 0) + COALESCE(1.0 / (:rrfK + k.pos), 0) AS score,
               v.pos AS vector_rank,
               k.pos AS keyword_rank
        FROM vector_hits v
        FULL OUTER JOIN keyword_hits k ON k.id = v.id
    )
    SELECT c.id, c.document_id, d.filename, c.page_number, c.chunk_index, c.content,
           1 - (c.embedding <=> CAST(:embedding AS vector)) AS similarity,
           f.score, f.vector_rank, f.keyword_rank
    FROM fused f
    JOIN chunks c ON c.id = f.id
    JOIN documents d ON d.id = c.document_id
    ORDER BY f.score DESC, c.id
    LIMIT :limit
    """;

    private final JdbcClient jdbc;

    public ChunkSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Reads an integer-valued column that may be bigint (ROW_NUMBER) or NULL. */
    private static Integer nullableInt(ResultSet rs, String column)
        throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : (int) value;
    }

    public List<RetrievedChunk> vectorSearch(
        float[] embedding,
        int limit,
        List<UUID> documentIds
    ) {
        List<RetrievedChunk> hits = statement(VECTOR_SQL, documentIds)
            .param("embedding", PgVector.toLiteral(embedding))
            .param("limit", limit)
            .query(MAPPER)
            .list();
        return IntStream.range(0, hits.size())
            .mapToObj(i -> hits.get(i).withVectorRank(i + 1))
            .toList();
    }

    public List<RetrievedChunk> keywordSearch(
        float[] embedding,
        String tsQuery,
        int limit,
        List<UUID> documentIds
    ) {
        List<RetrievedChunk> hits = statement(KEYWORD_SQL, documentIds)
            .param("embedding", PgVector.toLiteral(embedding))
            .param("tsquery", tsQuery)
            .param("limit", limit)
            .query(MAPPER)
            .list();
        return IntStream.range(0, hits.size())
            .mapToObj(i -> hits.get(i).withKeywordRank(i + 1))
            .toList();
    }

    public List<RetrievedChunk> hybridSearch(
        float[] embedding,
        String tsQuery,
        int limit,
        int pool,
        int rrfK,
        List<UUID> documentIds
    ) {
        return statement(HYBRID_SQL, documentIds)
            .param("embedding", PgVector.toLiteral(embedding))
            .param("tsquery", tsQuery)
            .param("limit", limit)
            .param("pool", pool)
            .param("rrfK", rrfK)
            .query(MAPPER)
            .list();
    }

    private JdbcClient.StatementSpec statement(
        String sql,
        List<UUID> documentIds
    ) {
        boolean filtered = documentIds != null && !documentIds.isEmpty();
        JdbcClient.StatementSpec spec = jdbc.sql(
            sql.replace("{{filter}}", filtered ? DOCUMENT_FILTER : "")
        );
        if (!filtered) {
            return spec;
        }
        String arrayLiteral = documentIds
            .stream()
            .map(UUID::toString)
            .collect(Collectors.joining(",", "{", "}"));
        return spec.param("documentIds", arrayLiteral);
    }
}
