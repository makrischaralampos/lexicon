package dev.makris.lexicon.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AttributionVerifierTest {

    private static final double MIN_SUPPORT = 0.5;

    private static final String RETENTION =
        "Customer data is retained for 24 months. " +
        "Application logs are kept for 90 days. Database backups are encrypted with AES-256 and retained for 35 days.";
    private static final String PASSWORDS =
        "Passwords must be at least 14 characters long and must never be reused.";

    private static AttributionVerifier.Verified verify(
        String answer,
        String... sources
    ) {
        return AttributionVerifier.verify(
            answer,
            List.of(sources),
            MIN_SUPPORT
        );
    }

    @Test
    void aWrongCitationNumberIsRepairedFromTheEvidence() {
        AttributionVerifier.Verified result = verify(
            "Application logs are kept for 90 days [2].",
            RETENTION,
            PASSWORDS
        );

        assertThat(result.text()).isEqualTo(
            "Application logs are kept for 90 days [1]."
        );
        assertThat(result.cited()).containsExactly(1);
        assertThat(result.modelCited()).containsExactly(2);
    }

    @Test
    void aSupportedAnswerWithoutMarkersStillGetsCitations() {
        AttributionVerifier.Verified result = verify(
            "Passwords must be at least 14 characters long.",
            RETENTION,
            PASSWORDS
        );

        assertThat(result.text()).isEqualTo(
            "Passwords must be at least 14 characters long [2]."
        );
        assertThat(result.cited()).containsExactly(2);
        assertThat(result.modelCited()).isEmpty();
    }

    @Test
    void aFigureThatIsNotInTheSourceIsNeverSupported() {
        AttributionVerifier.Verified result = verify(
            "Application logs are kept for 30 days [1].",
            RETENTION,
            PASSWORDS
        );

        assertThat(result.cited()).isEmpty();
        assertThat(result.text()).isEmpty();
        assertThat(result.droppedSentences()).isEqualTo(1);
    }

    @Test
    void unsupportedSentencesAreDroppedAndSupportedOnesKept() {
        AttributionVerifier.Verified result = verify(
            "Application logs are kept for 90 days [1]. The CEO is Jane Smith [1].",
            RETENTION,
            PASSWORDS
        );

        assertThat(result.text()).isEqualTo(
            "Application logs are kept for 90 days [1]."
        );
        assertThat(result.droppedSentences()).isEqualTo(1);
    }

    @Test
    void eachSentenceIsCitedToItsOwnSource() {
        AttributionVerifier.Verified result = verify(
            "Application logs are kept for 90 days. Passwords must be at least 14 characters long.",
            RETENTION,
            PASSWORDS
        );

        assertThat(result.text()).isEqualTo(
            "Application logs are kept for 90 days [1]. Passwords must be at least 14 characters long [2]."
        );
        assertThat(result.cited()).containsExactly(1, 2);
    }

    @Test
    void equallySupportiveSourcesAreBothCited() {
        AttributionVerifier.Verified result = verify(
            "Application logs are kept for 90 days.",
            RETENTION,
            "Note: " + RETENTION
        );

        assertThat(result.text()).isEqualTo(
            "Application logs are kept for 90 days [1][2]."
        );
        assertThat(result.cited()).containsExactly(1, 2);
    }

    @Test
    void lineBreaksBetweenSentencesArePreserved() {
        AttributionVerifier.Verified result = verify(
            "Application logs are kept for 90 days.\nPasswords must be at least 14 characters long.",
            RETENTION,
            PASSWORDS
        );

        assertThat(result.text()).isEqualTo(
            "Application logs are kept for 90 days [1].\nPasswords must be at least 14 characters long [2]."
        );
    }

    @Test
    void markersThatPointAtNonexistentSourcesAreIgnored() {
        AttributionVerifier.Verified result = verify(
            "Application logs are kept for 90 days [9].",
            RETENTION
        );

        assertThat(result.modelCited()).isEmpty();
        assertThat(result.cited()).containsExactly(1);
    }

    @Test
    void emptyAnswersVerifyToNothing() {
        AttributionVerifier.Verified result = verify("", RETENTION);

        assertThat(result.text()).isEmpty();
        assertThat(result.cited()).isEmpty();
    }
}
