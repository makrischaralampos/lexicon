package dev.makris.lexicon.eval;

import dev.makris.lexicon.answer.AnswerGenerator;
import dev.makris.lexicon.answer.AnswerResult;
import dev.makris.lexicon.answer.AnswerService;
import dev.makris.lexicon.answer.AnswerStatus;
import dev.makris.lexicon.answer.Citation;
import dev.makris.lexicon.answer.NoAnswerReason;
import dev.makris.lexicon.chunk.ChunkRepository;
import dev.makris.lexicon.retrieval.SearchMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("eval")
class AnswerEvaluator {

    private static final Logger log = LoggerFactory.getLogger(
        AnswerEvaluator.class
    );

    private static final String JUDGE_SYSTEM = """
    You are a strict fact-checker. You are given numbered sources and an answer. \
    Reply with exactly one word: YES if every factual claim in the answer is supported \
    by the sources, otherwise NO.
    """;

    /**
     * {@code passed}: answerable questions need an ANSWERED result containing the expected facts,
     * unanswerable ones need a refusal. {@code faithful} is null when the judge did not run.
     */
    record AnswerOutcome(
        EvalQuestion question,
        AnswerStatus status,
        NoAnswerReason reason,
        boolean passed,
        boolean citationHit,
        double citationPrecision,
        Boolean faithful,
        double topSimilarity,
        long totalMs,
        String answer
    ) {}

    private final AnswerService answers;
    private final ChunkRepository chunks;
    private final AnswerGenerator generator;
    private final EvalProperties properties;

    AnswerEvaluator(
        AnswerService answers,
        ChunkRepository chunks,
        AnswerGenerator generator,
        EvalProperties properties
    ) {
        this.answers = answers;
        this.chunks = chunks;
        this.generator = generator;
        this.properties = properties;
    }

    AnswerOutcome evaluate(EvalQuestion q, List<UUID> documentIds, int k) {
        long start = System.nanoTime();
        AnswerResult result = answers.answer(
            q.question(),
            SearchMode.HYBRID,
            k,
            documentIds
        );
        long totalMs = (System.nanoTime() - start) / 1_000_000;
        double topSimilarity = result.stats().topSimilarity();

        if (!q.answerable()) {
            boolean refused = result.status() == AnswerStatus.NO_ANSWER;
            return new AnswerOutcome(
                q,
                result.status(),
                result.reason(),
                refused,
                false,
                0,
                null,
                topSimilarity,
                totalMs,
                result.answer()
            );
        }
        if (result.status() != AnswerStatus.ANSWERED) {
            return new AnswerOutcome(
                q,
                result.status(),
                result.reason(),
                false,
                false,
                0,
                null,
                topSimilarity,
                totalMs,
                result.answer()
            );
        }

        List<String> citedContents = new ArrayList<>();
        int relevantCitations = 0;
        for (Citation citation : result.citations()) {
            String content = chunks.findContent(citation.chunkId()).orElse("");
            citedContents.add(content);
            if (q.isRelevant(citation.filename(), content)) {
                relevantCitations++;
            }
        }

        boolean passed = TextMatch.satisfiesAll(
            result.answer(),
            q.answerContains()
        );
        double precision = result.citations().isEmpty()
            ? 0
            : (double) relevantCitations / result.citations().size();
        Boolean faithful = properties.judge()
            ? judge(q.question(), result, citedContents)
            : null;

        return new AnswerOutcome(
            q,
            result.status(),
            result.reason(),
            passed,
            relevantCitations > 0,
            precision,
            faithful,
            topSimilarity,
            totalMs,
            result.answer()
        );
    }

    /** LLM-as-judge: is every claim in the answer supported by the cited sources? */
    private Boolean judge(
        String question,
        AnswerResult result,
        List<String> citedContents
    ) {
        StringBuilder sources = new StringBuilder();
        for (int i = 0; i < result.citations().size(); i++) {
            sources
                .append('[')
                .append(result.citations().get(i).index())
                .append("] ")
                .append(citedContents.get(i))
                .append("\n\n");
        }
        String user =
            "Sources:\n" +
            sources +
            "Question: " +
            question +
            "\n\n" +
            "Answer: " +
            result.answer() +
            "\n\n" +
            "Is every factual claim in the answer supported by the sources? Reply YES or NO.";
        try {
            return generator
                .generate(JUDGE_SYSTEM, user)
                .strip()
                .toUpperCase(Locale.ROOT)
                .startsWith("YES");
        } catch (RuntimeException e) {
            log.warn(
                "Judge call failed; faithfulness not scored for this question",
                e
            );
            return null;
        }
    }
}
