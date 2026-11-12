# Lexicon evaluation report

- Generated: 2026-11-12T09:17:27.763407675Z
- Chat model: `llama3.2:3b`, embedding model: `nomic-embed-text`
- Configuration: chunks of 800 characters with 120 overlap, embedding prefixes off, judge on
- Corpus: 7 documents, 15 chunks
- Questions: 50 (42 answerable, 8 unanswerable)

## Retrieval (k = 5)

| Mode | hit@1 | hit@3 | hit@5 | MRR | Avg latency |
|---|---|---|---|---|---|
| VECTOR | 71.4% | 97.6% | 97.6% | 0.829 | 9 ms |
| KEYWORD | 88.1% | 97.6% | 100.0% | 0.925 | 8 ms |
| HYBRID | 85.7% | 100.0% | 100.0% | 0.925 | 10 ms |

### hit@5 by question category

| Category | Questions | VECTOR | KEYWORD | HYBRID |
|---|---|---|---|---|
| DIRECT | 20 | 100.0% | 100.0% | 100.0% |
| PARAPHRASE | 12 | 100.0% | 100.0% | 100.0% |
| EXACT_TOKEN | 8 | 87.5% | 100.0% | 100.0% |
| MULTI_FACT | 2 | 100.0% | 100.0% | 100.0% |

## Answer quality (hybrid retrieval)

| Metric | Result |
|---|---|
| Answer accuracy (answerable questions) | 95.2% |
| False refusals (answerable but refused) | 4.8% |
| Citation hit rate (answered) | 67.5% |
| Citation precision (answered) | 67.5% |
| Faithfulness, LLM judge | 65.0% (40 judged) |
| Correct refusals (unanswerable questions) | 100.0% |
| Avg end-to-end latency | 537 ms |

## Similarity gate analysis

- Lowest top-similarity among answerable questions: 0.504
- Highest top-similarity among unanswerable questions: 0.653
- Current gate 0.40: 0 answerable question(s) fall below it, 8 unanswerable question(s) pass it
- The groups overlap, so the gate alone cannot separate them; the model-refusal and citation layers handle the rest.

## Failures

| Id | Category | Question | Outcome | Answer |
|---|---|---|---|---|
| q04 | DIRECT | Where is the office located? | NO_ANSWER / UNCITED_ANSWER | I couldn't find the answer to this in the uploaded documents. |
| n04 | PARAPHRASE | How much am I paid per kilometre when I use my own car for work? | NO_ANSWER / MODEL_DECLINED | I couldn't find the answer to this in the uploaded documents. |

### Answered without a relevant citation

| Id | Question | Answer |
|---|---|---|
| q06 | How much is the on-call stipend per week? | The on-call engineer receives a stipend of 150 euros per week [2]. |
| q13 | How long are application logs kept? | Application logs are kept for 90 days [2]. |
| q20 | Which cloud regions does Atlas run in? | Atlas runs in Frankfurt and Dublin [2]. |
| q07 | How quickly must an engineer respond when they get paged while on call? | An engineer must acknowledge a page within 15 minutes [2]. |
| q11 | How soon must I tell the security team after spotting something suspicious? | You must report any suspected security incident to the security team at security@orbitaldynamics.example within 2 hours … |
| q19 | What happens when I exceed the API rate limit and receive HTTP 429? | Requests above the limit receive HTTP 429 with a Retry-After header [2]. |
| q15 | How fast are critical vulnerabilities fixed and how much are the rewards? | Critical findings are acknowledged within 24 hours and fixed within 7 days [2]. Rewards for critical findings range from… |
| n01 | What is the hotel limit per night in Europe? | The hotel limit per night in Europe is 140 euros [2]. |
| n02 | How much is the daily meal allowance on business trips? | The daily meal allowance on business trips is 45 euros [2]. |
| n08 | How long does it take to get a standard system access request completed? | All access requests are filed as tickets with the prefix IT- in the service portal. Standard requests are completed with… |
| n14 | When will version 1 of the API stop working? | Version 1 of the API will be removed on 31 March 2027 [2]. |
| n16 | Within how many minutes must an incident commander be assigned for an S1 incident? | For an S1 incident, an incident commander must be assigned within 10 minutes [1]. |
| n17 | What prefix do postmortem follow-up tickets use? | Follow-up actions are tracked as tickets with the prefix OPS- [2]. |

Hybrid retrieval misses (no relevant chunk in top 5): none

## Definitions

- **Relevant chunk:** from the expected document and containing a gold evidence phrase.
- **hit@k:** share of answerable questions with a relevant chunk in the top k. **MRR:** mean of 1/rank of the first relevant chunk.
- **Answer accuracy:** the answer is ANSWERED and contains all expected facts.
- **Citation hit rate / precision:** answers with at least one relevant citation / share of citations that are relevant.
- **Faithfulness:** an LLM judge (the configured chat model) checks that every claim is supported by the cited chunks. A small local model is a weak judge; treat this as a trend, not an absolute.
- **Correct refusal:** an unanswerable question (including prompt-injection attempts) is answered with NO_ANSWER.
