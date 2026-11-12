package dev.makris.lexicon.answer;

/** Events of a streamed answer: provisional tokens, then one authoritative result. */
public sealed interface AnswerEvent {
    record Token(String text) implements AnswerEvent {}

    record Result(AnswerResult result) implements AnswerEvent {}
}
