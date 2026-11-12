package dev.makris.lexicon.eval;

import java.util.List;

/**
 * One evaluation question. Answerable questions carry gold evidence strings; a chunk is relevant when it
 * comes from the expected document and contains any of them. Questions without evidence must be refused.
 */
public record EvalQuestion(
    String id,
    String category,
    String question,
    String expectedDocument,
    List<String> evidence,
    List<String> answerContains
) {
    public EvalQuestion {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        answerContains =
            answerContains == null ? List.of() : List.copyOf(answerContains);
    }

    public boolean answerable() {
        return !evidence.isEmpty();
    }

    public boolean isRelevant(String filename, String content) {
        return (
            filename != null &&
            filename.equals(expectedDocument) &&
            TextMatch.containsAny(content, evidence)
        );
    }
}
