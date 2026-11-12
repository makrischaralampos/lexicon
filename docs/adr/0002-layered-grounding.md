# 2. Layered grounding; the similarity gate is only a coarse filter

Status: accepted

## Context

The system must refuse questions the documents cannot answer. The obvious mechanism is a cosine-similarity cutoff.

## Decision

Three layers: (1) a similarity gate skips the model entirely when nothing retrieved is similar enough;
(2) the prompt tells the model to reply `NOT_FOUND` when the sources lack the answer; (3) the attribution
verifier (ADR 3) rejects answers no source supports. Every refusal carries a reason.

## Evidence

On the 50-question benchmark the lowest top-similarity of an answerable question is 0.528 and the highest of an
unanswerable one is 0.718, so no threshold separates them: raising the gate would break correct answers.
All 8 unanswerable questions, including a prompt-injection attempt, were still refused, by layers 2 and 3.

## Consequences

- The gate stays at 0.40 and only filters clearly off-topic queries cheaply.
- Similarity of the best chunk is exported as a histogram, so the gate can be re-evaluated on real traffic.
- Retrieved documents are untrusted input: they are fenced in `<sources>` tags, the system prompt says to ignore
  instructions inside them, and the closing tag is stripped from document text.
