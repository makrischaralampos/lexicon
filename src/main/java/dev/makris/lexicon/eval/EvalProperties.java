package dev.makris.lexicon.eval;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lexicon.eval")
public record EvalProperties(
    @DefaultValue("sample-docs") String corpusDir,
    @DefaultValue("eval/reports") String reportDir,
    @DefaultValue("5") int k,
    @DefaultValue("true") boolean judge,
    List<EvalQuestion> questions,
    @DefaultValue Thresholds thresholds
) {
    public EvalProperties {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }

    /** Minimum acceptable results; the run exits with a non-zero code when one is not met. */
    public record Thresholds(
        @DefaultValue("0.0") double hybridHitAtK,
        @DefaultValue("0.0") double refusalRate,
        @DefaultValue("0.0") double answerAccuracy
    ) {}
}
