package dev.makris.lexicon.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.makris.lexicon.config.AnswerProperties;
import dev.makris.lexicon.observability.LexiconMetrics;
import dev.makris.lexicon.retrieval.RetrievalService;
import dev.makris.lexicon.retrieval.RetrievedChunk;
import dev.makris.lexicon.retrieval.SearchMode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class AnswerStreamingTest {

    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final AnswerProperties properties = new AnswerProperties(
        0.40,
        8000,
        0.50
    );

    private static AnswerGenerator streaming(
        AtomicBoolean called,
        String... parts
    ) {
        return new AnswerGenerator() {
            @Override
            public String generate(String systemPrompt, String userPrompt) {
                called.set(true);
                return String.join("", parts);
            }

            @Override
            public Flux<String> stream(String systemPrompt, String userPrompt) {
                called.set(true);
                return Flux.fromArray(parts);
            }
        };
    }

    private void retrievalReturns(double similarity) {
        RetrievedChunk chunk = new RetrievedChunk(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "handbook.txt",
            null,
            0,
            "Vacation: 25 days per year.",
            0.03,
            similarity,
            1,
            1
        );
        when(retrieval.effectiveTopK(any())).thenReturn(5);
        when(retrieval.retrieve(any(), any(), anyInt(), any())).thenReturn(
            List.of(chunk)
        );
    }

    private List<AnswerEvent> collect(AnswerGenerator generator) {
        return new AnswerService(
            retrieval,
            generator,
            properties,
            new LexiconMetrics(new SimpleMeterRegistry())
        )
            .stream("How many vacation days?", SearchMode.HYBRID, null, null)
            .collectList()
            .block();
    }

    private static String joinedTokens(List<AnswerEvent> events) {
        return events
            .stream()
            .filter(AnswerEvent.Token.class::isInstance)
            .map(e -> ((AnswerEvent.Token) e).text())
            .reduce("", String::concat);
    }

    private static AnswerResult resultOf(List<AnswerEvent> events) {
        AnswerEvent last = events.get(events.size() - 1);
        assertThat(last).isInstanceOf(AnswerEvent.Result.class);
        return ((AnswerEvent.Result) last).result();
    }

    @Test
    void tokensAreStreamedThenValidatedResultArrivesLast() {
        retrievalReturns(0.62);

        List<AnswerEvent> events = collect(
            streaming(
                new AtomicBoolean(),
                "Employees get 25 ",
                "days of vacation [1]."
            )
        );

        assertThat(joinedTokens(events)).isEqualTo(
            "Employees get 25 days of vacation [1]."
        );
        AnswerResult result = resultOf(events);
        assertThat(result.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(result.citations()).hasSize(1);
        assertThat(
            events.stream().filter(AnswerEvent.Result.class::isInstance)
        ).hasSize(1);
    }

    @Test
    void notFoundReplyIsNeverShownAsTokens() {
        retrievalReturns(0.62);

        List<AnswerEvent> events = collect(
            streaming(new AtomicBoolean(), "NOT_", "FOUND")
        );

        assertThat(joinedTokens(events)).isEmpty();
        assertThat(resultOf(events).reason()).isEqualTo(
            NoAnswerReason.MODEL_DECLINED
        );
    }

    @Test
    void anUnsupportedStreamedAnswerEndsWithARefusalResult() {
        retrievalReturns(0.62);

        List<AnswerEvent> events = collect(
            streaming(new AtomicBoolean(), "The CEO is Jane Smith.")
        );

        assertThat(resultOf(events).reason()).isEqualTo(
            NoAnswerReason.UNSUPPORTED_ANSWER
        );
    }

    @Test
    void irrelevantContextEmitsOnlyAResultAndNeverCallsTheModel() {
        retrievalReturns(0.20);
        AtomicBoolean called = new AtomicBoolean();

        List<AnswerEvent> events = collect(
            streaming(called, "should not be used")
        );

        assertThat(events).hasSize(1);
        assertThat(resultOf(events).reason()).isEqualTo(
            NoAnswerReason.NO_RELEVANT_CONTEXT
        );
        assertThat(called).isFalse();
    }
}
