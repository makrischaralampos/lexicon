package dev.makris.lexicon.eval;

import java.util.List;
import java.util.Locale;

/** Case- and whitespace-insensitive text matching used by the evaluation. */
final class TextMatch {

    private TextMatch() {}

    static String normalize(String text) {
        return text == null
            ? ""
            : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    static boolean containsAny(String text, List<String> needles) {
        String haystack = normalize(text);
        return needles
            .stream()
            .map(TextMatch::normalize)
            .anyMatch(n -> !n.isEmpty() && haystack.contains(n));
    }

    /** Every requirement must be met; a requirement may offer alternatives separated by '|'. */
    static boolean satisfiesAll(String text, List<String> requirements) {
        String haystack = normalize(text);
        for (String requirement : requirements) {
            boolean met = false;
            for (String alternative : requirement.split("\\|")) {
                String needle = normalize(alternative);
                if (!needle.isEmpty() && haystack.contains(needle)) {
                    met = true;
                    break;
                }
            }
            if (!met) {
                return false;
            }
        }
        return true;
    }
}
