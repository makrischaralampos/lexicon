package dev.makris.lexicon.answer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CitationParserTest {

    @Test
    void validMarkersAreKept() {
        CitationParser.Parsed parsed = CitationParser.parse(
            "Employees get 25 days [1].",
            3
        );

        assertThat(parsed.text()).isEqualTo("Employees get 25 days [1].");
        assertThat(parsed.cited()).containsExactly(1);
    }

    @Test
    void commaSeparatedMarkersAreNormalised() {
        CitationParser.Parsed parsed = CitationParser.parse(
            "A [1][3]. B [2, 3].",
            3
        );

        assertThat(parsed.text()).isEqualTo("A [1][3]. B [2][3].");
        assertThat(parsed.cited()).containsExactly(1, 3, 2);
    }

    @Test
    void outOfRangeMarkersAreRemoved() {
        CitationParser.Parsed parsed = CitationParser.parse(
            "The claim [9].",
            3
        );

        assertThat(parsed.text()).isEqualTo("The claim.");
        assertThat(parsed.cited()).isEmpty();
    }

    @Test
    void mixedValidAndInvalidKeepsOnlyValid() {
        CitationParser.Parsed parsed = CitationParser.parse("Fact [1, 9].", 3);

        assertThat(parsed.text()).isEqualTo("Fact [1].");
        assertThat(parsed.cited()).containsExactly(1);
    }

    @Test
    void zeroIsNotAValidSource() {
        assertThat(CitationParser.parse("Fact [0].", 3).cited()).isEmpty();
    }

    @Test
    void textWithoutMarkersHasNoCitations() {
        CitationParser.Parsed parsed = CitationParser.parse(
            "Just an answer.",
            3
        );

        assertThat(parsed.text()).isEqualTo("Just an answer.");
        assertThat(parsed.cited()).isEmpty();
    }
}
