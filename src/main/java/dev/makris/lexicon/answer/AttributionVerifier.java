package dev.makris.lexicon.answer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Verifies a model answer against the sources it was given, instead of trusting the model's [n] markers.
 * Each sentence is scored against every source by content-word coverage; every figure in a sentence must
 * appear in the source. Supported sentences are re-cited to the sources that actually contain them, and
 * unsupported sentences are dropped.
 */
public final class AttributionVerifier {

    /**
     * @param text            the answer with verified citations; empty when nothing was supported
     * @param cited           1-based numbers of the sources the answer is attributed to, ascending
     * @param modelCited      the valid source numbers the model itself cited (for diagnostics)
     * @param droppedSentences sentences removed because no source supports them
     */
    public record Verified(
        String text,
        List<Integer> cited,
        List<Integer> modelCited,
        int droppedSentences
    ) {}

    private static final Pattern MARKER = Pattern.compile(
        "\\s*\\[(\\d{1,3}(?:\\s*,\\s*\\d{1,3})*)\\]"
    );
    private static final Pattern BOUNDARY = Pattern.compile(
        "(?<=[.!?])\\s+|\\n+"
    );
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile(
        "^(.*?)([.!?]+)$",
        Pattern.DOTALL
    );

    /** A source within this distance of the best score is considered equally good support. */
    private static final double TOLERANCE = 0.10;
    private static final int MAX_CITATIONS_PER_SENTENCE = 2;

    private static final Set<String> STOPWORDS = Set.copyOf(
        List.of(
            "the",
            "and",
            "for",
            "are",
            "was",
            "were",
            "with",
            "that",
            "this",
            "from",
            "they",
            "them",
            "their",
            "have",
            "has",
            "had",
            "can",
            "may",
            "must",
            "will",
            "would",
            "should",
            "its",
            "per",
            "all",
            "any",
            "but",
            "you",
            "your",
            "our",
            "who",
            "what",
            "when",
            "where",
            "which",
            "how",
            "also",
            "each",
            "into",
            "than",
            "then",
            "there",
            "these",
            "those",
            "been",
            "being",
            "does",
            "did",
            "about",
            "over",
            "such",
            "only",
            "both",
            "according",
            "based",
            "provided",
            "sources",
            "source"
        )
    );

    private AttributionVerifier() {}

    public static Verified verify(
        String answer,
        List<String> sources,
        double minSupport
    ) {
        List<Set<String>> sourceTokens = sources
            .stream()
            .map(AttributionVerifier::tokens)
            .toList();
        List<Integer> modelCited = modelCitations(answer, sources.size());
        String clean = MARKER.matcher(answer).replaceAll("").strip();

        List<String> pieces = new ArrayList<>();
        List<String> separators = new ArrayList<>();
        Matcher boundary = BOUNDARY.matcher(clean);
        int last = 0;
        while (boundary.find()) {
            pieces.add(clean.substring(last, boundary.start()));
            separators.add(boundary.group());
            last = boundary.end();
        }
        pieces.add(clean.substring(last));

        StringBuilder out = new StringBuilder();
        Set<Integer> cited = new TreeSet<>();
        int dropped = 0;

        for (int i = 0; i < pieces.size(); i++) {
            String piece = pieces.get(i).strip();
            Set<String> claim = tokens(piece);
            if (claim.isEmpty()) {
                continue; // filler such as "However," has nothing to verify
            }
            List<Integer> picks = supportingSources(
                claim,
                sourceTokens,
                minSupport
            );
            if (picks.isEmpty()) {
                dropped++;
                continue;
            }
            if (out.length() > 0) {
                out.append(
                    i > 0 && separators.get(i - 1).contains("\n") ? "\n" : " "
                );
            }
            out.append(render(piece, picks));
            cited.addAll(picks);
        }
        return new Verified(
            out.toString(),
            List.copyOf(cited),
            modelCited,
            dropped
        );
    }

    private static List<Integer> supportingSources(
        Set<String> claim,
        List<Set<String>> sources,
        double minSupport
    ) {
        List<String> figures = claim
            .stream()
            .filter(AttributionVerifier::isNumber)
            .toList();
        double[] scores = new double[sources.size()];
        double best = 0;

        for (int i = 0; i < sources.size(); i++) {
            Set<String> source = sources.get(i);
            if (!source.containsAll(figures)) {
                continue; // a figure that is not in the source cannot be supported by it
            }
            long matched = claim.stream().filter(source::contains).count();
            scores[i] = (double) matched / claim.size();
            best = Math.max(best, scores[i]);
        }
        if (best <= 0 || best < minSupport) {
            return List.of();
        }

        double floor = Math.max(minSupport, best - TOLERANCE);
        return IntStream.range(0, scores.length)
            .boxed()
            .filter(i -> scores[i] >= floor)
            .sorted(
                Comparator.comparingDouble(
                    (Integer i) -> -scores[i]
                ).thenComparing(Comparator.naturalOrder())
            )
            .limit(MAX_CITATIONS_PER_SENTENCE)
            .map(i -> i + 1)
            .sorted()
            .toList();
    }

    private static String render(String piece, List<Integer> picks) {
        String core = piece;
        String punctuation = "";
        Matcher matcher = TRAILING_PUNCTUATION.matcher(piece);
        if (matcher.matches()) {
            core = matcher.group(1).stripTrailing();
            punctuation = matcher.group(2);
        }
        String markers = picks
            .stream()
            .map(n -> "[" + n + "]")
            .collect(Collectors.joining());
        return core + " " + markers + punctuation;
    }

    private static List<Integer> modelCitations(
        String answer,
        int sourceCount
    ) {
        Set<Integer> numbers = new TreeSet<>();
        Matcher matcher = MARKER.matcher(answer);
        while (matcher.find()) {
            for (String part : matcher.group(1).split(",")) {
                int number = Integer.parseInt(part.strip());
                if (number >= 1 && number <= sourceCount) {
                    numbers.add(number);
                }
            }
        }
        return List.copyOf(numbers);
    }

    /** Lowercased content words (lightly stemmed) and numbers; stopwords and very short words are ignored. */
    static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (isNumber(token)) {
                out.add(token);
            } else if (token.length() >= 3 && !STOPWORDS.contains(token)) {
                out.add(stem(token));
            }
        }
        return out;
    }

    private static boolean isNumber(String token) {
        return token.chars().allMatch(Character::isDigit);
    }

    private static String stem(String token) {
        return token.length() > 3 &&
            token.endsWith("s") &&
            !token.endsWith("ss")
            ? token.substring(0, token.length() - 1)
            : token;
    }
}
