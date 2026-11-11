package dev.makris.lexicon.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void shortTextProducesASingleChunk() {
        assertThat(
            new TextChunker(1000, 100).split("Hello world.")
        ).containsExactly("Hello world.");
    }

    @Test
    void blankTextProducesNoChunks() {
        assertThat(new TextChunker(1000, 100).split("  \n\n \t ")).isEmpty();
    }

    @Test
    void longTextIsSplitWithinTheSizeLimitAndChunksOverlap() {
        String text = "The quick brown fox jumps over the lazy dog. ".repeat(
            200
        );
        int size = 500;

        List<String> chunks = new TextChunker(size, 80).split(text);

        assertThat(chunks.size()).isGreaterThan(5);
        assertThat(chunks).allSatisfy(chunk ->
            assertThat(chunk.length()).isLessThanOrEqualTo(size)
        );
        for (int i = 0; i < chunks.size() - 1; i++) {
            String current = chunks.get(i);
            String tail = current.substring(current.length() - 15);
            assertThat(chunks.get(i + 1)).contains(tail);
        }
    }

    @Test
    void paragraphBoundariesArePreferred() {
        String first = "word ".repeat(60).strip();
        String second = "other ".repeat(50).strip();

        List<String> chunks = new TextChunker(400, 50).split(
            first + "\n\n" + second
        );

        assertThat(chunks.get(0)).isEqualTo(first);
        assertThat(chunks.get(chunks.size() - 1)).endsWith(second);
    }

    @Test
    void invalidConfigurationIsRejected() {
        assertThatThrownBy(() -> new TextChunker(50, 0)).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() -> new TextChunker(500, 250)).isInstanceOf(
            IllegalArgumentException.class
        );
    }
}
