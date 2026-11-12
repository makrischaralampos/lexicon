package dev.makris.lexicon.answer;

import dev.makris.lexicon.common.ApiException;
import dev.makris.lexicon.config.AnswerProperties;
import dev.makris.lexicon.retrieval.RetrievalService;
import dev.makris.lexicon.retrieval.RetrievedChunk;
import dev.makris.lexicon.retrieval.SearchMode;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(
        AnswerService.class
    );
    private static final String NOT_FOUND_TOKEN = "NOT_FOUND";

    private final RetrievalService retrieval;
    private final AnswerGenerator generator;
    private final AnswerProperties properties;

    public AnswerService(
        RetrievalService retrieval,
        AnswerGenerator generator,
        AnswerProperties properties
    ) {
        this.retrieval = retrieval;
        this.generator = generator;
        this.properties = properties;
    }

    public AnswerResult answer(
        String question,
        SearchMode mode,
        Integer requestedTopK,
        List<UUID> documentIds
    ) {
        long retrievalStart = System.nanoTime();
        int topK = retrieval.effectiveTopK(requestedTopK);
        List<RetrievedChunk> retrieved = retrieval.retrieve(
            question,
            mode,
            topK,
            documentIds
        );
        long retrievalMs = millisSince(retrievalStart);

        double topSimilarity = retrieved
            .stream()
            .mapToDouble(RetrievedChunk::similarity)
            .max()
            .orElse(0.0);

        // Layer 1: nothing relevant retrieved, so do not even call the model.
        if (retrieved.isEmpty() || topSimilarity < properties.minSimilarity()) {
            return finish(
                AnswerResult.noAnswer(
                    NoAnswerReason.NO_RELEVANT_CONTEXT,
                    stats(retrieved.size(), topSimilarity, retrievalMs, 0)
                )
            );
        }

        PromptFactory.BuiltPrompt prompt = PromptFactory.build(
            question,
            retrieved,
            properties.maxContextChars()
        );

        long generationStart = System.nanoTime();
        String raw = generate(prompt.user());
        long generationMs = millisSince(generationStart);
        AnswerResult.Stats stats = stats(
            retrieved.size(),
            topSimilarity,
            retrievalMs,
            generationMs
        );

        String text = raw.strip();

        // Layer 2: the model itself says the sources do not contain the answer.
        if (text.isEmpty() || text.toUpperCase().contains(NOT_FOUND_TOKEN)) {
            log.warn(
                "Model declined to answer. Raw output: {}",
                abbreviate(text)
            );
            return finish(
                AnswerResult.noAnswer(NoAnswerReason.MODEL_DECLINED, stats)
            );
        }

        // Layer 3: every answer must cite at least one real source.
        CitationParser.Parsed parsed = CitationParser.parse(
            text,
            prompt.included().size()
        );
        if (parsed.cited().isEmpty()) {
            log.warn(
                "Model answer had no valid citation. Raw output: {}",
                abbreviate(text)
            );
            return finish(
                AnswerResult.noAnswer(NoAnswerReason.UNCITED_ANSWER, stats)
            );
        }

        List<Citation> citations = parsed
            .cited()
            .stream()
            .sorted()
            .map(n -> Citation.of(n, prompt.included().get(n - 1)))
            .toList();
        return finish(AnswerResult.answered(parsed.text(), citations, stats));
    }

    private String generate(String userPrompt) {
        try {
            return generator.generate(PromptFactory.SYSTEM_PROMPT, userPrompt);
        } catch (RuntimeException e) {
            log.error("Chat model call failed", e);
            throw ApiException.serviceUnavailable(
                "The chat model is unavailable. Is Ollama running?"
            );
        }
    }

    private static AnswerResult finish(AnswerResult result) {
        log.info(
            "Answer status={} reason={} chunks={} topSimilarity={} retrievalMs={} generationMs={}",
            result.status(),
            result.reason(),
            result.stats().chunksRetrieved(),
            String.format("%.3f", result.stats().topSimilarity()),
            result.stats().retrievalMs(),
            result.stats().generationMs()
        );
        return result;
    }

    private static AnswerResult.Stats stats(
        int chunks,
        double topSimilarity,
        long retrievalMs,
        long generationMs
    ) {
        return new AnswerResult.Stats(
            chunks,
            topSimilarity,
            retrievalMs,
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
}
