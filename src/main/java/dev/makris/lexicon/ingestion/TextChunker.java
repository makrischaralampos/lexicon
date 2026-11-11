package dev.makris.lexicon.ingestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into overlapping chunks of at most {@code chunkSize} characters, preferring to break
 * at paragraph, then line, then sentence, then word boundaries.
 */
public final class TextChunker {

    private static final String[] SEPARATORS = { "\n\n", "\n", ". ", " " };
    private static final int MAX_WORD_SHIFT = 40;

    private final int chunkSize;
    private final int overlap;

    public TextChunker(int chunkSize, int overlap) {
        if (chunkSize < 100) {
            throw new IllegalArgumentException(
                "chunkSize must be at least 100"
            );
        }
        if (overlap < 0 || overlap >= chunkSize / 2) {
            throw new IllegalArgumentException(
                "overlap must be between 0 and chunkSize / 2 (exclusive)"
            );
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<String> split(String raw) {
        String text = normalize(raw);
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        int start = 0;
        while (start < length) {
            int hardEnd = Math.min(start + chunkSize, length);
            int end =
                hardEnd < length ? findBreak(text, start, hardEnd) : hardEnd;
            String chunk = text.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= length) {
                break;
            }
            start = nextStart(text, end);
        }
        return chunks;
    }

    private int findBreak(String text, int start, int hardEnd) {
        int minEnd = start + chunkSize / 2;
        for (String separator : SEPARATORS) {
            int index = text.lastIndexOf(
                separator,
                hardEnd - separator.length()
            );
            if (index >= minEnd) {
                return index + separator.length();
            }
        }
        return hardEnd;
    }

    private int nextStart(String text, int end) {
        int start = end - overlap;
        if (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            int i = start;
            int limit = Math.min(end, start + MAX_WORD_SHIFT);
            while (i < limit && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i < limit) {
                start = i + 1;
            }
        }
        return start;
    }

    private static String normalize(String raw) {
        return raw
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replaceAll("[ \\t]+", " ")
            .replaceAll(" *\\n *", "\n")
            .replaceAll("\\n{3,}", "\n\n")
            .strip();
    }
}
