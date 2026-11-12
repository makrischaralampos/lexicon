package dev.makris.lexicon.answer;

public enum NoAnswerReason {
    /** Retrieval found nothing similar enough to the question; the model was not called. */
    NO_RELEVANT_CONTEXT,
    /** The model said the sources do not contain the answer. */
    MODEL_DECLINED,
    /** No sentence of the model's answer could be verified against the retrieved sources. */
    UNSUPPORTED_ANSWER,
}
