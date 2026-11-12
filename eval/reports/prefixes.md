# Lexicon evaluation report

- Generated: 2026-11-12T09:16:34.437619277Z
- Chat model: `llama3.2:3b`, embedding model: `nomic-embed-text`
- Configuration: chunks of 400 characters with 80 overlap, embedding prefixes on, judge on
- Corpus: 7 documents, 39 chunks
- Questions: 50 (42 answerable, 8 unanswerable)

## Retrieval (k = 5)

| Mode | hit@1 | hit@3 | hit@5 | MRR | Avg latency |
|---|---|---|---|---|---|
| VECTOR | 85.7% | 100.0% | 100.0% | 0.925 | 14 ms |
| KEYWORD | 90.5% | 97.6% | 100.0% | 0.946 | 10 ms |
| HYBRID | 95.2% | 100.0% | 100.0% | 0.976 | 14 ms |

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
| Citation hit rate (answered) | 64.3% |
| Citation precision (answered) | 64.3% |
| Faithfulness, LLM judge | 52.4% (42 judged) |
| Correct refusals (unanswerable questions) | 100.0% |
| Avg end-to-end latency | 356 ms |

## Similarity gate analysis

- Lowest top-similarity among answerable questions: 0.528
- Highest top-similarity among unanswerable questions: 0.718
- Current gate 0.40: 0 answerable question(s) fall below it, 8 unanswerable question(s) pass it
- The groups overlap, so the gate alone cannot separate them; the model-refusal and citation layers handle the rest.

## Failures

None.

### Answered without a relevant citation

| Id | Question | Answer |
|---|---|---|
| q13 | How long are application logs kept? | Application logs are kept for 90 days [2]. |
| q16 | How often does Atlas refresh vessel positions over satellite? | Positions are refreshed every 30 seconds for vessels reporting over satellite [5]. |
| q20 | Which cloud regions does Atlas run in? | Atlas runs in Frankfurt and Dublin [2]. |
| q21 | How long does a bulk export of more than a million rows take? | Bulk exports of more than 1 million rows must be requested through support and are delivered within 2 business days [2]. |
| q03 | How many days per week may staff work from home? | Staff may work remotely up to 3 days per week [2]. |
| q22 | What plans does Atlas offer and which of them include a dedicated account manager? | Atlas offers three plans: Starter, Professional, and Enterprise [2]. Only Professional and Enterprise plans include a de… |
| n02 | How much is the daily meal allowance on business trips? | The daily meal allowance on business trips is 45 euros [3]. |
| n03 | How far ahead of departure do I have to book a trip? | Business travel must be booked through the company travel portal at least 14 days before departure [2]. |
| n05 | When is business class allowed on a flight? | Business class is allowed only for flights longer than 6 hours and requires approval from a director [2]. |
| n08 | How long does it take to get a standard system access request completed? | Standard access requests are completed within 2 business days [2]. |
| n10 | Which prefix do access request tickets use? | All access requests are filed as tickets with the prefix IT- in the service portal [5]. |
| n13 | How many times are failed webhook deliveries retried? | Failed webhook deliveries are retried 5 times with exponential backoff before the webhook is disabled [2]. |
| n14 | When will version 1 of the API stop working? | Version 1 of the API will stop working on 31 March 2027 [2]. |
| n18 | How often are internal updates sent during an S1 incident? | Internal updates go to the incident chat channel every 30 minutes during an S1 incident [2]. |
| n19 | Who is allowed to talk to customers while an incident is going on? | Only the incident commander is allowed to speak to customers during an incident [3]. |

Hybrid retrieval misses (no relevant chunk in top 5): none

## Definitions

- **Relevant chunk:** from the expected document and containing a gold evidence phrase.
- **hit@k:** share of answerable questions with a relevant chunk in the top k. **MRR:** mean of 1/rank of the first relevant chunk.
- **Answer accuracy:** the answer is ANSWERED and contains all expected facts.
- **Citation hit rate / precision:** answers with at least one relevant citation / share of citations that are relevant.
- **Faithfulness:** an LLM judge (the configured chat model) checks that every claim is supported by the cited chunks. A small local model is a weak judge; treat this as a trend, not an absolute.
- **Correct refusal:** an unanswerable question (including prompt-injection attempts) is answered with NO_ANSWER.
