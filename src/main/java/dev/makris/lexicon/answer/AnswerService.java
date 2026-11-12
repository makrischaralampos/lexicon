package dev.makris.lexicon.answer;

import dev.makris.lexicon.common.ApiException;
import dev.makris.lexicon.config.AnswerProperties;
import dev.makris.lexicon.observability.LexiconMetrics;
import dev.makris.lexicon.retrieval.RetrievalService;
import dev.makris.lexicon.retrieval.RetrievedChunk;
import dev.makris.lexicon.retrieval.SearchMode;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(
        AnswerService.class
    );
    private static final String NOT_FOUND_TOKEN = "NOT_FOUND";

    private record Retrieval(
        List<RetrievedChunk> chunks,
        double topSimilarity,
        long retrievalMs
    ) {}

    private final RetrievalService retrievalService;
    private final AnswerGenerator generator;
    private final AnswerProperties properties;
    private final LexiconMetrics metrics;

    public AnswerService(
        RetrievalService retrievalService,
        AnswerGenerator generator,
        AnswerProperties properties,
        LexiconMetrics metrics
    ) {
        this.retrievalService = retrievalService;
        this.generator = generator;
        this.properties = properties;
        this.metrics = metrics;
    }

    public AnswerResult answer(
        String question,
        SearchMode mode,
        Integer requestedTopK,
        List<UUID> documentIds
    ) {
        Retrieval found = retrieve(question, mode, requestedTopK, documentIds);

        // Layer 1: nothing relevant retrieved, so do not even call the model.
        if (isIrrelevant(found)) {
            return finish(
                AnswerResult.noAnswer(
                    NoAnswerReason.NO_RELEVANT_CONTEXT,
                    stats(found, 0)
                )
            );
        }

        PromptFactory.BuiltPrompt prompt = PromptFactory.build(
            question,
            found.chunks(),
            properties.maxContextChars()
        );
        long generationStart = System.nanoTime();
        String raw = generate(prompt.user());
        return evaluate(raw, prompt, found, millisSince(generationStart));
    }

    /**
     * Streams provisional tokens followed by exactly one authoritative {@link AnswerEvent.Result}.
     * Runs on a blocking-friendly scheduler because retrieval uses JDBC.
     */
    public Flux<AnswerEvent> stream(
        String question,
        SearchMode mode,
        Integer requestedTopK,
        List<UUID> documentIds
    ) {
        return Flux.<AnswerEvent>defer(() -> {
            Retrieval found = retrieve(
                question,
                mode,
                requestedTopK,
                documentIds
            );

            if (isIrrelevant(found)) {
                return Flux.<AnswerEvent>just(
                    new AnswerEvent.Result(
                        finish(
                            AnswerResult.noAnswer(
                                NoAnswerReason.NO_RELEVANT_CONTEXT,
                                stats(found, 0)
                            )
                        )
                    )
                );
            }

            PromptFactory.BuiltPrompt prompt = PromptFactory.build(
                question,
                found.chunks(),
                properties.maxContextChars()
            );
            long generationStart = System.nanoTime();
            StringBuilder full = new StringBuilder();
            StreamGate gate = new StreamGate();

            Flux<AnswerEvent> tokens = generator
                .stream(PromptFactory.SYSTEM_PROMPT, prompt.user())
                .onErrorMap(AnswerService::toUnavailable)
                .concatMap(delta -> {
                    full.append(delta);
                    return emit(gate.accept(delta));
                })
                .concatWith(Flux.defer(() -> emit(gate.flush())));

            Mono<AnswerEvent> result = Mono.fromSupplier(() ->
                new AnswerEvent.Result(
                    evaluate(
                        full.toString(),
                        prompt,
                        found,
                        millisSince(generationStart)
                    )
                )
            );

            return tokens.concatWith(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private Retrieval retrieve(
        String question,
        SearchMode mode,
        Integer requestedTopK,
        List<UUID> documentIds
    ) {
        long start = System.nanoTime();
        int topK = retrievalService.effectiveTopK(requestedTopK);
        List<RetrievedChunk> chunks = retrievalService.retrieve(
            question,
            mode,
            topK,
            documentIds
        );
        double top = chunks
            .stream()
            .mapToDouble(RetrievedChunk::similarity)
            .max()
            .orElse(0.0);
        return new Retrieval(chunks, top, millisSince(start));
    }

    private boolean isIrrelevant(Retrieval found) {
        return (
            found.chunks().isEmpty() ||
            found.topSimilarity() < properties.minSimilarity()
        );
    }

    /** Layers 2 and 3: model refusal, then verification of every sentence against the sources. */
    private AnswerResult evaluate(
        String raw,
        PromptFactory.BuiltPrompt prompt,
        Retrieval found,
        long generationMs
    ) {
        AnswerResult.Stats stats = stats(found, generationMs);
        String text = raw.strip();

        if (text.isEmpty() || text.toUpperCase().contains(NOT_FOUND_TOKEN)) {
            log.warn(
                "Model declined to answer. Raw output: {}",
                abbreviate(text)
            );
            return finish(
                AnswerResult.noAnswer(NoAnswerReason.MODEL_DECLINED, stats)
            );
        }

        List<String> sources = prompt
            .included()
            .stream()
            .map(RetrievedChunk::content)
            .toList();
        AttributionVerifier.Verified verified = AttributionVerifier.verify(
            text,
            sources,
            properties.minSupport()
        );

        if (verified.cited().isEmpty()) {
            log.warn(
                "No sentence of the model answer is supported by the sources. Raw output: {}",
                abbreviate(text)
            );
            return finish(
                AnswerResult.noAnswer(NoAnswerReason.UNSUPPORTED_ANSWER, stats)
            );
        }
        if (verified.droppedSentences() > 0) {
            log.info(
                "Dropped {} unsupported sentence(s) from the model answer",
                verified.droppedSentences()
            );
        }
        if (
            !new HashSet<>(verified.modelCited()).equals(
                new HashSet<>(verified.cited())
            )
        ) {
            log.info(
                "Citation repair: model cited {} but the sources support {}",
                verified.modelCited(),
                verified.cited()
            );
        }

        List<Citation> citations = verified
            .cited()
            .stream()
            .map(n -> Citation.of(n, prompt.included().get(n - 1)))
            .toList();
        return finish(AnswerResult.answered(verified.text(), citations, stats));
    }

    private String generate(String userPrompt) {
        try {
            return generator.generate(PromptFactory.SYSTEM_PROMPT, userPrompt);
        } catch (RuntimeException e) {
            throw (ApiException) toUnavailable(e);
        }
    }

    private static Throwable toUnavailable(Throwable e) {
        if (e instanceof ApiException) {
            return e;
        }
        log.error("Chat model call failed", e);
        return ApiException.serviceUnavailable(
            "The chat model is unavailable. Is Ollama running?"
        );
    }

    private static Flux<AnswerEvent> emit(String text) {
        return text.isEmpty()
            ? Flux.empty()
            : Flux.just(new AnswerEvent.Token(text));
    }

    private AnswerResult finish(AnswerResult result) {
        log.info(
            "Answer status={} reason={} chunks={} topSimilarity={} retrievalMs={} generationMs={}",
            result.status(),
            result.reason(),
            result.stats().chunksRetrieved(),
            String.format("%.3f", result.stats().topSimilarity()),
            result.stats().retrievalMs(),
            result.stats().generationMs()
        );
        metrics.answerCompleted(result);
        return result;
    }

    private static AnswerResult.Stats stats(
        Retrieval found,
        long generationMs
    ) {
        return new AnswerResult.Stats(
            found.chunks().size(),
            found.topSimilarity(),
            found.retrievalMs(),
            generationMs
        );
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String abbreviate(String text) {
        String flat = text.replaceAll("\\s+", " ");
        return flat.length() > 500 ? flat.substring(0, 500) + "…" : flat;
    }

    /**
     * Holds back the first few characters of a stream until we can tell whether the model is replying
     * NOT_FOUND, in which case nothing is shown to the client.
     */
    private static final class StreamGate {

        private final StringBuilder buffer = new StringBuilder();
        private boolean decided;
        private boolean suppressed;

        String accept(String delta) {
            if (decided) {
                return suppressed ? "" : delta;
            }
            buffer.append(delta);
            String trimmed = buffer.toString().stripLeading();
            if (trimmed.length() < NOT_FOUND_TOKEN.length()) {
                return "";
            }
            decided = true;
            suppressed = trimmed.toUpperCase().startsWith(NOT_FOUND_TOKEN);
            return suppressed ? "" : buffer.toString();
        }

        /** Called when the stream ends; anything still buffered is too short to be NOT_FOUND. */
        String flush() {
            if (decided) {
                return "";
            }
            decided = true;
            return buffer.toString();
        }
    }
}
