package dev.makris.lexicon.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.makris.lexicon.common.ApiException;
import dev.makris.lexicon.config.AnswerProperties;
import dev.makris.lexicon.retrieval.RetrievalService;
import dev.makris.lexicon.retrieval.RetrievedChunk;
import dev.makris.lexicon.retrieval.SearchMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AnswerServiceTest {

    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final AnswerProperties properties = new AnswerProperties(
        0.40,
        8000
    );

    private AnswerService service(AnswerGenerator generator) {
        return new AnswerService(retrieval, generator, properties);
    }

    private void retrievalReturns(List<RetrievedChunk> chunks) {
        when(retrieval.effectiveTopK(any())).thenReturn(5);
        when(retrieval.retrieve(any(), any(), anyInt(), any())).thenReturn(
            chunks
        );
    }

    private static RetrievedChunk chunk(
        String filename,
        Integer page,
        String content,
        double similarity
    ) {
        return new RetrievedChunk(
            UUID.randomUUID(),
            UUID.randomUUID(),
            filename,
            page,
            0,
            content,
            0.03,
            similarity,
            1,
            1
        );
    }

    private AnswerResult ask(AnswerService service) {
        return service.answer(
            "How many vacation days?",
            SearchMode.HYBRID,
            null,
            null
        );
    }

    @Test
    void noChunksMeansNoAnswerWithoutCallingTheModel() {
        retrievalReturns(List.of());
        AtomicBoolean called = new AtomicBoolean();

        AnswerResult result = ask(
            service((system, user) -> {
                called.set(true);
                return "anything";
            })
        );

        assertThat(result.status()).isEqualTo(AnswerStatus.NO_ANSWER);
        assertThat(result.reason()).isEqualTo(
            NoAnswerReason.NO_RELEVANT_CONTEXT
        );
        assertThat(called).isFalse();
    }

    @Test
    void lowSimilarityMeansNoAnswerWithoutCallingTheModel() {
        retrievalReturns(List.of(chunk("a.txt", null, "unrelated", 0.30)));
        AtomicBoolean called = new AtomicBoolean();

        AnswerResult result = ask(
            service((system, user) -> {
                called.set(true);
                return "anything";
            })
        );

        assertThat(result.reason()).isEqualTo(
            NoAnswerReason.NO_RELEVANT_CONTEXT
        );
        assertThat(called).isFalse();
    }

    @Test
    void modelDecliningIsReported() {
        retrievalReturns(List.of(chunk("a.txt", null, "text", 0.60)));

        AnswerResult result = ask(service((system, user) -> "NOT_FOUND"));

        assertThat(result.status()).isEqualTo(AnswerStatus.NO_ANSWER);
        assertThat(result.reason()).isEqualTo(NoAnswerReason.MODEL_DECLINED);
        assertThat(result.answer()).isEqualTo(AnswerResult.NO_ANSWER_MESSAGE);
    }

    @Test
    void uncitedAnswersAreRejected() {
        retrievalReturns(List.of(chunk("a.txt", null, "text", 0.60)));

        AnswerResult result = ask(
            service((system, user) -> "Employees get 25 days.")
        );

        assertThat(result.status()).isEqualTo(AnswerStatus.NO_ANSWER);
        assertThat(result.reason()).isEqualTo(NoAnswerReason.UNCITED_ANSWER);
    }

    @Test
    void citationsToNonexistentSourcesDoNotCount() {
        retrievalReturns(List.of(chunk("a.txt", null, "text", 0.60)));

        AnswerResult result = ask(
            service((system, user) -> "Employees get 25 days [7].")
        );

        assertThat(result.reason()).isEqualTo(NoAnswerReason.UNCITED_ANSWER);
    }

    @Test
    void validCitedAnswerIsReturnedWithOnlyTheCitedSources() {
        RetrievedChunk first = chunk(
            "handbook.txt",
            null,
            "Vacation: 25 days per year.",
            0.62
        );
        RetrievedChunk second = chunk(
            "faq.pdf",
            3,
            "On-call stipend is 150 euros.",
            0.55
        );
        retrievalReturns(List.of(first, second));

        AnswerResult result = ask(
            service(
                (system, user) -> "Employees get 25 days of vacation [1][9]."
            )
        );

        assertThat(result.status()).isEqualTo(AnswerStatus.ANSWERED);
        assertThat(result.reason()).isNull();
        assertThat(result.answer()).isEqualTo(
            "Employees get 25 days of vacation [1]."
        );
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).index()).isEqualTo(1);
        assertThat(result.citations().get(0).filename()).isEqualTo(
            "handbook.txt"
        );
        assertThat(result.citations().get(0).chunkId()).isEqualTo(
            first.chunkId()
        );
    }

    @Test
    void generatorFailureBecomesServiceUnavailable() {
        retrievalReturns(List.of(chunk("a.txt", null, "text", 0.60)));

        assertThatThrownBy(() ->
            ask(
                service((system, user) -> {
                    throw new IllegalStateException("connection refused");
                })
            )
        )
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("chat model is unavailable");
    }
}
