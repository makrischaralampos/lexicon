package dev.makris.lexicon.answer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extracts [n] citation markers from a model answer and removes any that do not refer to a real
 * source. Markers such as "[1, 3]" are normalised to "[1][3]".
 */
public final class CitationParser {

    private static final Pattern MARKER = Pattern.compile(
        "\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)\\]"
    );

    public record Parsed(String text, List<Integer> cited) {}

    private CitationParser() {}

    public static Parsed parse(String text, int sourceCount) {
        Set<Integer> cited = new LinkedHashSet<>();
        StringBuilder out = new StringBuilder();
        Matcher matcher = MARKER.matcher(text);

        while (matcher.find()) {
            List<Integer> valid = new ArrayList<>();
            for (String part : matcher.group(1).split(",")) {
                int number = Integer.parseInt(part.strip());
                if (number >= 1 && number <= sourceCount) {
                    valid.add(number);
                }
            }
            cited.addAll(valid);
            String replacement = valid
                .stream()
                .map(n -> "[" + n + "]")
                .collect(Collectors.joining());
            matcher.appendReplacement(
                out,
                Matcher.quoteReplacement(replacement)
            );
        }
        matcher.appendTail(out);

        String cleaned = out
            .toString()
            .replaceAll("[ \\t]{2,}", " ")
            .replaceAll("[ \\t]+([.,;:!?])", "$1")
            .strip();
        return new Parsed(cleaned, List.copyOf(cited));
    }
}
