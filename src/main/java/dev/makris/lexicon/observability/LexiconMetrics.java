package dev.makris.lexicon.observability;

import dev.makris.lexicon.answer.AnswerResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Domain metrics. The top-similarity histogram is the one to watch in production: plot answered vs refused
 * questions against it to decide whether the similarity gate needs adjusting.
 */
@Component
public class LexiconMetrics {

    private final MeterRegistry registry;
    private final DistributionSummary topSimilarity;
    private final DistributionSummary chunksPerDocument;
    private final Timer retrievalTimer;
    private final Timer generationTimer;

    public LexiconMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.topSimilarity = DistributionSummary.builder(
            "lexicon.retrieval.top.similarity"
        )
            .description(
                "Cosine similarity of the best retrieved chunk per question"
            )
            .serviceLevelObjectives(0.3, 0.4, 0.5, 0.6, 0.7, 0.8)
            .register(registry);
        this.chunksPerDocument = DistributionSummary.builder(
            "lexicon.ingestion.chunks"
        )
            .description("Chunks produced per ingested document")
            .serviceLevelObjectives(5, 10, 25, 50, 100, 250, 500)
            .register(registry);
        this.retrievalTimer = Timer.builder("lexicon.answer.retrieval")
            .description("Time spent retrieving chunks for an answer")
            .register(registry);
        this.generationTimer = Timer.builder("lexicon.answer.generation")
            .description("Time spent in the chat model for an answer")
            .register(registry);
    }

    public void answerCompleted(AnswerResult result) {
        Counter.builder("lexicon.answers")
            .description("Answers by outcome and refusal reason")
            .tag("status", result.status().name())
            .tag(
                "reason",
                result.reason() == null ? "none" : result.reason().name()
            )
            .register(registry)
            .increment();

        AnswerResult.Stats stats = result.stats();
        retrievalTimer.record(stats.retrievalMs(), TimeUnit.MILLISECONDS);
        if (stats.generationMs() > 0) {
            generationTimer.record(stats.generationMs(), TimeUnit.MILLISECONDS);
        }
        if (stats.chunksRetrieved() > 0) {
            topSimilarity.record(stats.topSimilarity());
        }
    }

    public void ingestionCreated(int chunks) {
        ingestion("created");
        chunksPerDocument.record(chunks);
    }

    public void ingestionDuplicate() {
        ingestion("duplicate");
    }

    public void ingestionFailed() {
        ingestion("failed");
    }

    private void ingestion(String result) {
        Counter.builder("lexicon.ingestion.documents")
            .description("Document uploads by outcome")
            .tag("result", result)
            .register(registry)
            .increment();
    }
}
