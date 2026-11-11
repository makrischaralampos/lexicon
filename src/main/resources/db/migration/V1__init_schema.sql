CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename        TEXT        NOT NULL,
    content_type    TEXT        NOT NULL,
    sha256          CHAR(64)    NOT NULL UNIQUE,
    size_bytes      BIGINT      NOT NULL,
    status          TEXT        NOT NULL
                    CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
    failure_reason  TEXT,
    page_count      INTEGER,
    chunk_count     INTEGER     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chunks (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id  UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index  INTEGER     NOT NULL,
    page_number  INTEGER,
    content      TEXT        NOT NULL,
    token_count  INTEGER,
    embedding    vector(768) NOT NULL,
    content_tsv  tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_chunks_document  ON chunks (document_id);
CREATE INDEX idx_chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunks_tsv       ON chunks USING gin (content_tsv);
