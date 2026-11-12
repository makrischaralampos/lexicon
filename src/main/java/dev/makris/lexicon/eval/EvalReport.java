package dev.makris.lexicon.eval;

import dev.makris.lexicon.answer.AnswerStatus;
import dev.makris.lexicon.eval.AnswerEvaluator.AnswerOutcome;
import dev.makris.lexicon.eval.RetrievalEvaluator.ModeResult;
import dev.makris.lexicon.retrieval.SearchMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Aggregates the evaluation outcomes and renders them as a Markdown report. */
final class EvalReport {

    record Meta(
        Instant generatedAt,
        String chatModel,
        String embeddingModel,
        double gate,
        int k
    ) {}

    record Summary(
        double hybridHitAtK,
        double refusalRate,
        double answerAccuracy
    ) {}

    private EvalReport() {}

    static Summary summarize(
        List<ModeResult> retrieval,
        List<AnswerOutcome> outcomes
    ) {
        double hybrid = retrieval
            .stream()
            .filter(r -> r.mode() == SearchMode.HYBRID)
            .mapToDouble(ModeResult::hitAtK)
            .findFirst()
            .orElse(0.0);
        return new Summary(
            hybrid,
            rate(unanswerable(outcomes), AnswerOutcome::passed),
            rate(answerable(outcomes), AnswerOutcome::passed)
        );
    }

    static String render(
        Meta meta,
        EvalCorpus.Loaded corpus,
        List<EvalQuestion> questions,
        List<ModeResult> retrieval,
        List<AnswerOutcome> outcomes
    ) {
        List<AnswerOutcome> answerable = answerable(outcomes);
        List<AnswerOutcome> unanswerable = unanswerable(outcomes);
        int k = meta.k();

        StringBuilder md = new StringBuilder();
        md.append("# Lexicon evaluation report\n\n");
        md.append("- Generated: ").append(meta.generatedAt()).append('\n');
        md.append("- Chat model: `")
            .append(meta.chatModel())
            .append("`, embedding model: `")
            .append(meta.embeddingModel())
            .append("`\n");
        md.append("- Corpus: ")
            .append(corpus.documentCount())
            .append(" documents, ")
            .append(corpus.chunkCount())
            .append(" chunks\n");
        md.append("- Questions: ")
            .append(outcomes.size())
            .append(" (")
            .append(answerable.size())
            .append(" answerable, ")
            .append(unanswerable.size())
            .append(" unanswerable)\n\n");

        // ---- Retrieval ----
        md.append("## Retrieval (k = ").append(k).append(")\n\n");
        md.append("| Mode | hit@1 | hit@3 | hit@")
            .append(k)
            .append(" | MRR | Avg latency |\n");
        md.append("|---|---|---|---|---|---|\n");
        for (ModeResult r : retrieval) {
            md.append(
                String.format(
                    Locale.ROOT,
                    "| %s | %s | %s | %s | %.3f | %.0f ms |\n",
                    r.mode(),
                    pct(r.hitAt1()),
                    pct(r.hitAt3()),
                    pct(r.hitAtK()),
                    r.mrr(),
                    r.avgMs()
                )
            );
        }

        md.append("\n### hit@").append(k).append(" by question category\n\n");
        md.append("| Category | Questions |");
        retrieval.forEach(r -> md.append(' ').append(r.mode()).append(" |"));
        md.append("\n|---|---|");
        retrieval.forEach(r -> md.append("---|"));
        md.append('\n');
        Map<String, List<EvalQuestion>> byCategory = new LinkedHashMap<>();
        for (EvalQuestion q : questions) {
            if (q.answerable()) {
                byCategory
                    .computeIfAbsent(q.category(), c -> new ArrayList<>())
                    .add(q);
            }
        }
        byCategory.forEach((category, list) -> {
            md.append("| ")
                .append(category)
                .append(" | ")
                .append(list.size())
                .append(" |");
            for (ModeResult r : retrieval) {
                long hits = list
                    .stream()
                    .filter(q -> {
                        int rank = r.ranks().getOrDefault(q.id(), 0);
                        return rank >= 1 && rank <= k;
                    })
                    .count();
                md.append(' ')
                    .append(pct((double) hits / list.size()))
                    .append(" |");
            }
            md.append('\n');
        });

        // ---- Answers ----
        List<AnswerOutcome> answered = answerable
            .stream()
            .filter(o -> o.status() == AnswerStatus.ANSWERED)
            .toList();
        List<AnswerOutcome> judged = answerable
            .stream()
            .filter(o -> o.faithful() != null)
            .toList();
        double avgLatency = outcomes
            .stream()
            .mapToLong(AnswerOutcome::totalMs)
            .average()
            .orElse(0);

        md.append("\n## Answer quality (hybrid retrieval)\n\n");
        md.append("| Metric | Result |\n|---|---|\n");
        md.append("| Answer accuracy (answerable questions) | ")
            .append(pct(rate(answerable, AnswerOutcome::passed)))
            .append(" |\n");
        md.append("| False refusals (answerable but refused) | ")
            .append(
                pct(rate(answerable, o -> o.status() == AnswerStatus.NO_ANSWER))
            )
            .append(" |\n");
        md.append("| Citation hit rate (answered) | ")
            .append(pct(rate(answered, AnswerOutcome::citationHit)))
            .append(" |\n");
        md.append("| Citation precision (answered) | ")
            .append(
                pct(
                    answered
                        .stream()
                        .mapToDouble(AnswerOutcome::citationPrecision)
                        .average()
                        .orElse(0)
                )
            )
            .append(" |\n");
        md.append("| Faithfulness, LLM judge | ")
            .append(
                judged.isEmpty()
                    ? "not run"
                    : pct(rate(judged, o -> Boolean.TRUE.equals(o.faithful())))
            )
            .append(" (")
            .append(judged.size())
            .append(" judged) |\n");
        md.append("| Correct refusals (unanswerable questions) | ")
            .append(pct(rate(unanswerable, AnswerOutcome::passed)))
            .append(" |\n");
        md.append(
            String.format(
                Locale.ROOT,
                "| Avg end-to-end latency | %.0f ms |\n",
                avgLatency
            )
        );

        // ---- Gate analysis ----
        double minAnswerable = answerable
            .stream()
            .mapToDouble(AnswerOutcome::topSimilarity)
            .min()
            .orElse(0);
        double maxUnanswerable = unanswerable
            .stream()
            .mapToDouble(AnswerOutcome::topSimilarity)
            .max()
            .orElse(0);
        long belowGate = answerable
            .stream()
            .filter(o -> o.topSimilarity() < meta.gate())
            .count();
        long aboveGate = unanswerable
            .stream()
            .filter(o -> o.topSimilarity() >= meta.gate())
            .count();

        md.append("\n## Similarity gate analysis\n\n");
        md.append(
            String.format(
                Locale.ROOT,
                "- Lowest top-similarity among answerable questions: %.3f%n",
                minAnswerable
            )
        );
        md.append(
            String.format(
                Locale.ROOT,
                "- Highest top-similarity among unanswerable questions: %.3f%n",
                maxUnanswerable
            )
        );
        md.append(
            String.format(
                Locale.ROOT,
                "- Current gate %.2f: %d answerable question(s) fall below it, %d unanswerable question(s) pass it%n",
                meta.gate(),
                belowGate,
                aboveGate
            )
        );
        md.append(
            maxUnanswerable < minAnswerable
                ? "- The two groups are separable: any gate between those two values would split them perfectly.\n"
                : "- The groups overlap, so the gate alone cannot separate them; the model-refusal and citation " +
                      "layers handle the rest.\n"
        );

        // ---- Failures ----
        md.append("\n## Failures\n\n");
        List<AnswerOutcome> failed = outcomes
            .stream()
            .filter(o -> !o.passed())
            .toList();
        if (failed.isEmpty()) {
            md.append("None.\n");
        } else {
            md.append(
                "| Id | Category | Question | Outcome | Answer |\n|---|---|---|---|---|\n"
            );
            for (AnswerOutcome o : failed) {
                String outcome =
                    o.status() + (o.reason() != null ? " / " + o.reason() : "");
                md.append("| ")
                    .append(o.question().id())
                    .append(" | ")
                    .append(o.question().category())
                    .append(" | ")
                    .append(cell(o.question().question()))
                    .append(" | ")
                    .append(outcome)
                    .append(" | ")
                    .append(cell(abbreviate(o.answer())))
                    .append(" |\n");
            }
        }

        retrieval
            .stream()
            .filter(r -> r.mode() == SearchMode.HYBRID)
            .findFirst()
            .ifPresent(hybrid -> {
                String misses = hybrid
                    .ranks()
                    .entrySet()
                    .stream()
                    .filter(e -> e.getValue() < 1 || e.getValue() > k)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.joining(", "));
                md.append(
                    "\nHybrid retrieval misses (no relevant chunk in top "
                )
                    .append(k)
                    .append("): ")
                    .append(misses.isEmpty() ? "none" : misses)
                    .append("\n");
            });

        // ---- Definitions ----
        md.append(
            """

            ## Definitions

            - **Relevant chunk:** from the expected document and containing a gold evidence phrase.
            - **hit@k:** share of answerable questions with a relevant chunk in the top k. **MRR:** mean of 1/rank of the first relevant chunk.
            - **Answer accuracy:** the answer is ANSWERED and contains all expected facts.
            - **Citation hit rate / precision:** answers with at least one relevant citation / share of citations that are relevant.
            - **Faithfulness:** an LLM judge (the configured chat model) checks that every claim is supported by the cited chunks. A small local model is a weak judge; treat this as a trend, not an absolute.
            - **Correct refusal:** an unanswerable question (including prompt-injection attempts) is answered with NO_ANSWER.
            """
        );
        return md.toString();
    }

    private static List<AnswerOutcome> answerable(
        List<AnswerOutcome> outcomes
    ) {
        return outcomes
            .stream()
            .filter(o -> o.question().answerable())
            .toList();
    }

    private static List<AnswerOutcome> unanswerable(
        List<AnswerOutcome> outcomes
    ) {
        return outcomes
            .stream()
            .filter(o -> !o.question().answerable())
            .toList();
    }

    private static double rate(
        List<AnswerOutcome> list,
        Predicate<AnswerOutcome> test
    ) {
        return list.isEmpty()
            ? 0.0
            : (double) list.stream().filter(test).count() / list.size();
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }

    private static String cell(String text) {
        return text == null
            ? ""
            : text.replace("|", "\\|").replaceAll("\\s+", " ");
    }

    private static String abbreviate(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ");
        return flat.length() > 120 ? flat.substring(0, 120) + "…" : flat;
    }
}
