# 1. Own the vector schema; fuse hybrid results in SQL

Status: accepted

## Context

Spring AI ships a `PgVectorStore` with its own table layout. Lexicon needs vector search and PostgreSQL
full-text search over the same rows, combined into one ranking, plus per-document filtering and page-level
citation metadata.

## Decision

Use Spring AI for the model abstractions (`EmbeddingModel`, `ChatClient`) and document readers, but keep our own
`documents` and `chunks` tables. `chunks` holds the 768-dimension `vector`, a generated `tsvector` column, an HNSW
index and a GIN index. Hybrid retrieval is a single SQL statement: two candidate lists (vector, full-text) joined
with a full outer join and scored by Reciprocal Rank Fusion, `1/(k+rank)` with k = 60.

## Consequences

- One round trip and one place where retrieval behaviour is defined and unit-testable.
- Keyword and vector ranks are returned with every hit, which makes retrieval explainable and measurable.
- We maintain the SQL and the migration ourselves, and changing the embedding model needs a new migration
  and a re-embed.
- The full-text query uses OR semantics (`a | b | c`), because natural-language questions contain words that
  never appear in the answer and AND would match nothing. Input is reduced to letters and digits, so users
  cannot inject tsquery operators.
