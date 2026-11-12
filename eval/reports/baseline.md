# Lexicon evaluation report

- Generated: 2026-11-12T09:15:47.070974561Z
- Chat model: `llama3.2:3b`, embedding model: `nomic-embed-text`
- Configuration: chunks of 400 characters with 80 overlap, embedding prefixes off, judge on
- Corpus: 7 documents, 39 chunks
- Questions: 50 (42 answerable, 8 unanswerable)

## Retrieval (k = 5)

| Mode | hit@1 | hit@3 | hit@5 | MRR | Avg latency |
|---|---|---|---|---|---|
| VECTOR | 85.7% | 100.0% | 100.0% | 0.925 | 18 ms |
| KEYWORD | 90.5% | 100.0% | 100.0% | 0.948 | 9 ms |
| HYBRID | 95.2% | 100.0% | 100.0% | 0.976 | 13 ms |

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
| Answer accuracy (answerable questions) | 95.2% |
| False refusals (answerable but refused) | 4.8% |
| Citation hit rate (answered) | 67.5% |
| Citation precision (answered) | 67.5% |
| Faithfulness, LLM judge | 57.5% (40 judged) |
| Correct refusals (unanswerable questions) | 100.0% |
| Avg end-to-end latency | 378 ms |

## Similarity gate analysis

- Lowest top-similarity among answerable questions: 0.489
- Highest top-similarity among unanswerable questions: 0.688
- Current gate 0.40: 0 answerable question(s) fall below it, 8 unanswerable question(s) pass it
- The groups overlap, so the gate alone cannot separate them; the model-refusal and citation layers handle the rest.

## Failures

| Id | Category | Question | Outcome | Answer |
|---|---|---|---|---|
| q04 | DIRECT | Where is the office located? | NO_ANSWER / UNCITED_ANSWER | I couldn't find the answer to this in the uploaded documents. |
| q06 | DIRECT | How much is the on-call stipend per week? | NO_ANSWER / MODEL_DECLINED | I couldn't find the answer to this in the uploaded documents. |

### Answered without a relevant citation

| Id | Question | Answer |
|---|---|---|
| q13 | How long are application logs kept? | Application logs are kept for 90 days [2]. |
| q16 | How often does Atlas refresh vessel positions over satellite? | Positions are refreshed every 30 seconds for vessels reporting over satellite [4]. |
| q20 | Which cloud regions does Atlas run in? | Atlas runs in Frankfurt and Dublin [2]. |
| q21 | How long does a bulk export of more than a million rows take? | Bulk exports of more than 1 million rows must be requested through support and are delivered within 2 business days [2]. |
| q09 | How often do employees get a new computer? | Laptops are refreshed every 36 months [2]. |
| q14 | Which encryption standard is used for database backups, AES-256 or something else? | Database backups are encrypted with AES-256 and retained for 35 days [3]. |
| n02 | How much is the daily meal allowance on business trips? | The daily meal allowance on business trips is 45 euros [3]. |
| n03 | How far ahead of departure do I have to book a trip? | All business travel must be booked through the company travel portal at least 14 days before departure [2]. |
| n08 | How long does it take to get a standard system access request completed? | Standard access requests are completed within 2 business days [2]. |
| n13 | How many times are failed webhook deliveries retried? | Failed webhook deliveries are retried 5 times with exponential backoff [2]. |
| n14 | When will version 1 of the API stop working? | Version 1 of the API will stop working on 31 March 2027 [2]. |
| n18 | How often are internal updates sent during an S1 incident? | Internal updates go to the incident chat channel every 30 minutes during an S1 incident [5]. |
| n19 | Who is allowed to talk to customers while an incident is going on? | Only the incident commander may speak to customers during an incident [2]. |

Hybrid retrieval misses (no relevant chunk in top 5): none

## Definitions

- **Relevant chunk:** from the expected document and containing a gold evidence phrase.
- **hit@k:** share of answerable questions with a relevant chunk in the top k. **MRR:** mean of 1/rank of the first relevant chunk.
- **Answer accuracy:** the answer is ANSWERED and contains all expected facts.
- **Citation hit rate / precision:** answers with at least one relevant citation / share of citations that are relevant.
- **Faithfulness:** an LLM judge (the configured chat model) checks that every claim is supported by the cited chunks. A small local model is a weak judge; treat this as a trend, not an absolute.
- **Correct refusal:** an unanswerable question (including prompt-injection attempts) is answered with NO_ANSWER.
