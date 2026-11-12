package dev.makris.lexicon.answer;

/** Produces the raw model text for a system + user prompt. Exists so grounding logic is testable. */
@FunctionalInterface
public interface AnswerGenerator {
    String generate(String systemPrompt, String userPrompt);
}
