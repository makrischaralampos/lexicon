package dev.makris.lexicon.answer;

import java.util.List;

public record AnswerResult(
    AnswerStatus status,
    NoAnswerReason reason,
    String answer,
    List<Citation> citations,
    Stats stats
) {
    public static final String NO_ANSWER_MESSAGE =
        "I couldn't find the answer to this in the uploaded documents.";

    public record Stats(
        int chunksRetrieved,
        double topSimilarity,
        long retrievalMs,
        long generationMs
    ) {}

    static AnswerResult answered(
        String answer,
        List<Citation> citations,
        Stats stats
    ) {
        return new AnswerResult(
            AnswerStatus.ANSWERED,
            null,
            answer,
            citations,
            stats
        );
    }

    static AnswerResult noAnswer(NoAnswerReason reason, Stats stats) {
        return new AnswerResult(
            AnswerStatus.NO_ANSWER,
            reason,
            NO_ANSWER_MESSAGE,
            List.of(),
            stats
        );
    }
}
