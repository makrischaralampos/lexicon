# 4. Evaluation relies on evidence strings, not chunk IDs

Status: accepted

## Context

The benchmark must keep working when chunk size, overlap or the embedding model change, which all change chunk
boundaries and IDs.

## Decision

A retrieved chunk counts as relevant when it comes from the expected document and contains a gold evidence phrase.
Before each run the harness lints the dataset: every evidence phrase must appear inside a single chunk, otherwise
the run fails fast instead of silently skewing metrics. The evaluation runs against its own database, truncated at
the start of every run (it refuses to run against a database whose name lacks "eval"), so configuration changes
always take effect. The run exits non-zero when hybrid hit@k, refusal rate or answer accuracy fall below thresholds.

## Consequences

- Chunking and model experiments are directly comparable; they drove the 400/80 chunking and the prefix defaults.
- The LLM-judge faithfulness metric is reported but never gates, because a 3B model grading itself is unreliable.
- Ties between equal full-text scores are broken by random IDs, which causes about one question of jitter in
  hit@1 between runs; deterministic tie-breaking is a known follow-up.
