# 3. Verify attribution instead of trusting model citations

Status: accepted

## Context

The first evaluation showed 100% answer accuracy but only 64% citation hit rate: the 3B model wrote correct
answers and pointed `[n]` at the wrong source, and the application log confirmed it ("model cited [2] but the
sources support [1]"). A faithfulness judge built on the same model scored the same problem from another angle.

## Decision

Ignore the model's markers. Split the answer into sentences and score each against every retrieved chunk by
content-word coverage (stopwords removed, light stemming). Every number in a sentence must appear in the chunk.
A sentence is cited to the best-scoring chunk(s), at most two within 0.10 of the best score, and is dropped when
the best score is below `min-support` (0.50). If nothing survives the answer becomes `UNSUPPORTED_ANSWER`.

## Results

Citation hit rate 64.3% to 100%, citation precision 64.3% to 98.8%, with answer accuracy and refusal rates unchanged.

## Consequences

- Citations are derived from evidence, and unsupported claims never reach the caller.
- The method is lexical: it catches wrong figures and invented facts but not negation or subtle paraphrase
  errors. It is cheap, deterministic and unit-tested; an NLI or LLM-based check could be layered on later.
- A model that mis-cites no longer causes false refusals.
