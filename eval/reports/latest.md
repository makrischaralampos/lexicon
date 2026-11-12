# Lexicon evaluation report

- Generated: 2026-11-12T08:59:26.247032477Z
- Chat model: `llama3.2:3b`, embedding model: `nomic-embed-text`
- Corpus: 3 documents, 6 chunks
- Questions: 28 (22 answerable, 6 unanswerable)

## Retrieval (k = 5)

| Mode | hit@1 | hit@3 | hit@5 | MRR | Avg latency |
|---|---|---|---|---|---|
| VECTOR | 86.4% | 100.0% | 100.0% | 0.932 | 504 ms |
| KEYWORD | 90.9% | 100.0% | 100.0% | 0.955 | 11 ms |
| HYBRID | 100.0% | 100.0% | 100.0% | 1.000 | 12 ms |

### hit@5 by question category

| Category | Questions | VECTOR | KEYWORD | HYBRID |
|---|---|---|---|---|
| DIRECT | 10 | 100.0% | 100.0% | 100.0% |
| PARAPHRASE | 6 | 100.0% | 100.0% | 100.0% |
| EXACT_TOKEN | 4 | 100.0% | 100.0% | 100.0% |
| MULTI_FACT | 2 | 100.0% | 100.0% | 100.0% |

## Answer quality (hybrid retrieval)

| Metric | Result |
|---|---|
| Answer accuracy (answerable questions) | 90.9% |
| False refusals (answerable but refused) | 9.1% |
| Citation hit rate (answered) | 80.0% |
| Citation precision (answered) | 80.0% |
| Faithfulness, LLM judge | 80.0% (20 judged) |
| Correct refusals (unanswerable questions) | 100.0% |
| Avg end-to-end latency | 540 ms |

## Similarity gate analysis

- Lowest top-similarity among answerable questions: 0.461
- Highest top-similarity among unanswerable questions: 0.656
- Current gate 0.40: 0 answerable question(s) fall below it, 6 unanswerable question(s) pass it
- The groups overlap, so the gate alone cannot separate them; the model-refusal and citation layers handle the rest.

## Failures

| Id | Category | Question | Outcome | Answer |
|---|---|---|---|---|
| q04 | DIRECT | Where is the office located? | NO_ANSWER / UNCITED_ANSWER | I couldn't find the answer to this in the uploaded documents. |
| q06 | DIRECT | How much is the on-call stipend per week? | NO_ANSWER / MODEL_DECLINED | I couldn't find the answer to this in the uploaded documents. |

Hybrid retrieval misses (no relevant chunk in top 5): none

## Definitions

- **Relevant chunk:** from the expected document and containing a gold evidence phrase.
- **hit@k:** share of answerable questions with a relevant chunk in the top k. **MRR:** mean of 1/rank of the first relevant chunk.
- **Answer accuracy:** the answer is ANSWERED and contains all expected facts.
- **Citation hit rate / precision:** answers with at least one relevant citation / share of citations that are relevant.
- **Faithfulness:** an LLM judge (the configured chat model) checks that every claim is supported by the cited chunks. A small local model is a weak judge; treat this as a trend, not an absolute.
- **Correct refusal:** an unanswerable question (including prompt-injection attempts) is answered with NO_ANSWER.
