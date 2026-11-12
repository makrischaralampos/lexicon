package dev.makris.lexicon.answer;

public enum NoAnswerReason {
    /** Retrieval found nothing similar enough to the question; the model was not called. */
    NO_RELEVANT_CONTEXT,
    /** The model said the sources do not contain the answer. */
    MODEL_DECLINED,
    /** The model answered but cited no valid source, so the answer cannot be verified. */
    UNCITED_ANSWER,
}
