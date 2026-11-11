package dev.makris.lexicon.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeywordQueryTest {

    @Test
    void termsAreLowercasedAndJoinedWithOr() {
        assertThat(KeywordQuery.toTsQuery("How many Vacation days?")).isEqualTo(
            "how | many | vacation | days"
        );
    }

    @Test
    void duplicatesAreRemoved() {
        assertThat(KeywordQuery.toTsQuery("days days DAYS")).isEqualTo("days");
    }

    @Test
    void tsqueryOperatorsCannotBeInjected() {
        assertThat(
            KeywordQuery.toTsQuery("a & b | !c ' ; -- (d) :*")
        ).isEqualTo("a | b | c | d");
    }

    @Test
    void punctuationOnlyInputProducesAnEmptyQuery() {
        assertThat(KeywordQuery.toTsQuery("?! ...")).isEmpty();
    }

    @Test
    void nonLatinLettersAreKept() {
        assertThat(KeywordQuery.toTsQuery("άδεια ημέρες")).isEqualTo(
            "άδεια | ημέρες"
        );
    }

    @Test
    void numberOfTermsIsCapped() {
        String input = java.util.stream.IntStream.range(0, 100)
            .mapToObj(i -> "term" + i)
            .collect(java.util.stream.Collectors.joining(" "));

        assertThat(KeywordQuery.toTsQuery(input).split(" \\| ")).hasSize(32);
    }
}
