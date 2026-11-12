package dev.makris.lexicon.eval;

import dev.makris.lexicon.retrieval.RetrievalService;
import dev.makris.lexicon.retrieval.RetrievedChunk;
import dev.makris.lexicon.retrieval.SearchMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("eval")
class RetrievalEvaluator {

    /** {@code ranks} maps question id to the 1-based rank of the first relevant chunk (0 = not found). */
    record ModeResult(
        SearchMode mode,
        int questions,
        double hitAt1,
        double hitAt3,
        double hitAtK,
        double mrr,
        double avgMs,
        Map<String, Integer> ranks
    ) {}

    private final RetrievalService retrieval;

    RetrievalEvaluator(RetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    ModeResult evaluate(
        SearchMode mode,
        List<EvalQuestion> questions,
        List<UUID> documentIds,
        int k
    ) {
        Map<String, Integer> ranks = new LinkedHashMap<>();
        int hit1 = 0;
        int hit3 = 0;
        int hitK = 0;
        double reciprocalRankSum = 0;
        long totalMs = 0;

        for (EvalQuestion q : questions) {
            long start = System.nanoTime();
            List<RetrievedChunk> chunks = retrieval.retrieve(
                q.question(),
                mode,
                k,
                documentIds
            );
            totalMs += (System.nanoTime() - start) / 1_000_000;

            int rank = 0;
            for (int i = 0; i < chunks.size(); i++) {
                if (
                    q.isRelevant(
                        chunks.get(i).filename(),
                        chunks.get(i).content()
                    )
                ) {
                    rank = i + 1;
                    break;
                }
            }
            ranks.put(q.id(), rank);

            if (rank > 0) {
                reciprocalRankSum += 1.0 / rank;
                if (rank <= 1) {
                    hit1++;
                }
                if (rank <= 3) {
                    hit3++;
                }
                if (rank <= k) {
                    hitK++;
                }
            }
        }

        int n = questions.size();
        return new ModeResult(
            mode,
            n,
            ratio(hit1, n),
            ratio(hit3, n),
            ratio(hitK, n),
            n == 0 ? 0 : reciprocalRankSum / n,
            n == 0 ? 0 : (double) totalMs / n,
            ranks
        );
    }

    private static double ratio(int part, int whole) {
        return whole == 0 ? 0 : (double) part / whole;
    }
}
