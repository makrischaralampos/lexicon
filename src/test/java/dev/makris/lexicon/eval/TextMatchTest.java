package dev.makris.lexicon.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextMatchTest {

    @Test
    void matchingIgnoresCaseAndWhitespace() {
        assertThat(
            TextMatch.containsAny(
                "Employees receive\n 25  DAYS of leave",
                List.of("25 days")
            )
        ).isTrue();
    }

    @Test
    void containsAnyNeedsAtLeastOneHit() {
        assertThat(
            TextMatch.containsAny("hello world", List.of("foo", "world"))
        ).isTrue();
        assertThat(
            TextMatch.containsAny("hello world", List.of("foo", "bar"))
        ).isFalse();
    }

    @Test
    void emptyNeedlesNeverMatch() {
        assertThat(TextMatch.containsAny("hello", List.of(""))).isFalse();
        assertThat(TextMatch.containsAny("hello", List.of())).isFalse();
    }

    @Test
    void allRequirementsMustBeMet() {
        assertThat(
            TextMatch.satisfiesAll(
                "7 days and 5000 euros",
                List.of("7", "5000")
            )
        ).isTrue();
        assertThat(
            TextMatch.satisfiesAll("7 days only", List.of("7", "5000"))
        ).isFalse();
    }

    @Test
    void alternativesWithinARequirementAreAccepted() {
        List<String> requirement = List.of("08:00|8 am");

        assertThat(
            TextMatch.satisfiesAll("opens at 8 AM", requirement)
        ).isTrue();
        assertThat(
            TextMatch.satisfiesAll("opens at 08:00", requirement)
        ).isTrue();
        assertThat(
            TextMatch.satisfiesAll("opens at nine", requirement)
        ).isFalse();
    }

    @Test
    void noRequirementsIsTriviallySatisfied() {
        assertThat(TextMatch.satisfiesAll("anything", List.of())).isTrue();
    }
}
