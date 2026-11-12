# Lexicon evaluation report

- Generated: 2026-11-12T10:00:31.260524764Z
- Chat model: `llama3.2:3b`, embedding model: `nomic-embed-text`
- Configuration: chunks of 400 characters with 80 overlap, embedding prefixes on, judge on
- Corpus: 7 documents, 39 chunks
- Questions: 50 (42 answerable, 8 unanswerable)

## Retrieval (k = 5)

| Mode | hit@1 | hit@3 | hit@5 | MRR | Avg latency |
|---|---|---|---|---|---|
| VECTOR | 85.7% | 100.0% | 100.0% | 0.925 | 12 ms |
| KEYWORD | 90.5% | 97.6% | 100.0% | 0.942 | 9 ms |
| HYBRID | 92.9% | 100.0% | 100.0% | 0.964 | 12 ms |

### hit@5 by question category

| Category | Questions | VECTOR | KEYWORD | HYBRID |
|---|---|---|---|---|
| DIRECT | 20 | 100.0% | 100.0% | 100.0% |
| PARAPHRASE | 12 | 100.0% | 100.0% | 100.0% |
| EXACT_TOKEN | 8 | 100.0% | 100.0% | 100.0% |
| MULTI_FACT | 2 | 100.0% | 100.0% | 100.0% |

## Answer quality (hybrid retrieval)

| Metric | Result |
|---|---|
| Answer accuracy (answerable questions) | 100.0% |
| False refusals (answerable but refused) | 0.0% |
| Citation hit rate (answered) | 100.0% |
| Citation precision (answered) | 98.8% |
| Faithfulness, LLM judge | 64.3% (42 judged) |
| Correct refusals (unanswerable questions) | 100.0% |
| Avg end-to-end latency | 379 ms |

## Similarity gate analysis

- Lowest top-similarity among answerable questions: 0.528
- Highest top-similarity among unanswerable questions: 0.718
- Current gate 0.40: 0 answerable question(s) fall below it, 8 unanswerable question(s) pass it
- The groups overlap, so the gate alone cannot separate them; the model-refusal and citation layers handle the rest.

## Failures

None.

### Answered without a relevant citation

None.

Hybrid retrieval misses (no relevant chunk in top 5): none

## Definitions

- **Relevant chunk:** from the expected document and containing a gold evidence phrase.
- **hit@k:** share of answerable questions with a relevant chunk in the top k. **MRR:** mean of 1/rank of the first relevant chunk.
- **Answer accuracy:** the answer is ANSWERED and contains all expected facts.
- **Citation hit rate / precision:** answers with at least one relevant citation / share of citations that are relevant.
- **Faithfulness:** an LLM judge (the configured chat model) checks that every claim is supported by the cited chunks. A small local model is a weak judge; treat this as a trend, not an absolute.
- **Correct refusal:** an unanswerable question (including prompt-injection attempts) is answered with NO_ANSWER.
