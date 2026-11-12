package dev.makris.lexicon.answer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Produces model text for a system + user prompt. Exists so grounding logic is testable. */
@FunctionalInterface
public interface AnswerGenerator {
    String generate(String systemPrompt, String userPrompt);

    /** Streams the answer in pieces. Implementations that cannot stream emit the whole answer at once. */
    default Flux<String> stream(String systemPrompt, String userPrompt) {
        return Mono.fromCallable(() ->
            generate(systemPrompt, userPrompt)
        ).flux();
    }
}
