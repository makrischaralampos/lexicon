package dev.makris.lexicon.retrieval;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts free text into a safe PostgreSQL tsquery with OR semantics ("a | b | c").
 * Only letters and digits survive, so user input can never inject tsquery operators.
 */
public final class KeywordQuery {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final int MAX_TERMS = 32;

    private KeywordQuery() {}

    public static String toTsQuery(String text) {
        Set<String> terms = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find() && terms.size() < MAX_TERMS) {
            terms.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return String.join(" | ", terms);
    }
}
