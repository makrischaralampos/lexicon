package dev.makris.lexicon.eval;

import dev.makris.lexicon.config.AnswerProperties;
import dev.makris.lexicon.eval.AnswerEvaluator.AnswerOutcome;
import dev.makris.lexicon.eval.RetrievalEvaluator.ModeResult;
import dev.makris.lexicon.retrieval.SearchMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs the whole evaluation when the application starts with the "eval" profile, writes the report and
 * exits: 0 = all thresholds met, 1 = a threshold was missed, 2 = the evaluation itself failed.
 */
@Component
@Profile("eval")
class EvalRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final EvalProperties properties;
    private final AnswerProperties answerProperties;
    private final EvalCorpus corpus;
    private final RetrievalEvaluator retrievalEvaluator;
    private final AnswerEvaluator answerEvaluator;
    private final ConfigurableApplicationContext context;
    private final String chatModel;
    private final String embeddingModel;

    EvalRunner(
        EvalProperties properties,
        AnswerProperties answerProperties,
        EvalCorpus corpus,
        RetrievalEvaluator retrievalEvaluator,
        AnswerEvaluator answerEvaluator,
        ConfigurableApplicationContext context,
        @Value("${spring.ai.ollama.chat.options.model}") String chatModel,
        @Value(
            "${spring.ai.ollama.embedding.options.model}"
        ) String embeddingModel
    ) {
        this.properties = properties;
        this.answerProperties = answerProperties;
        this.corpus = corpus;
        this.retrievalEvaluator = retrievalEvaluator;
        this.answerEvaluator = answerEvaluator;
        this.context = context;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode;
        try {
            exitCode = execute();
        } catch (Exception e) {
            log.error("Evaluation failed", e);
            exitCode = 2;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }

    private int execute() throws Exception {
        List<EvalQuestion> questions = properties.questions();
        if (questions.isEmpty()) {
            throw new IllegalStateException(
                "No evaluation questions were loaded. Is eval/questions.yml present?"
            );
        }

        EvalCorpus.Loaded loaded = corpus.load();
        List<EvalQuestion> answerable = questions
            .stream()
            .filter(EvalQuestion::answerable)
            .toList();
        int k = properties.k();

        List<ModeResult> retrieval = new ArrayList<>();
        for (SearchMode mode : SearchMode.values()) {
            log.info("Evaluating retrieval mode {}", mode);
            retrieval.add(
                retrievalEvaluator.evaluate(
                    mode,
                    answerable,
                    loaded.documentIds(),
                    k
                )
            );
        }

        List<AnswerOutcome> outcomes = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            EvalQuestion q = questions.get(i);
            log.info("Answering {} ({}/{})", q.id(), i + 1, questions.size());
            outcomes.add(answerEvaluator.evaluate(q, loaded.documentIds(), k));
        }

        EvalReport.Meta meta = new EvalReport.Meta(
            Instant.now(),
            chatModel,
            embeddingModel,
            answerProperties.minSimilarity(),
            k
        );
        String markdown = EvalReport.render(
            meta,
            loaded,
            questions,
            retrieval,
            outcomes
        );

        Path reportDir = Path.of(properties.reportDir());
        Files.createDirectories(reportDir);
        Path report = reportDir.resolve("latest.md");
        Files.writeString(report, markdown);
        System.out.println(markdown);
        log.info("Report written to {}", report.toAbsolutePath());

        return checkThresholds(EvalReport.summarize(retrieval, outcomes));
    }

    private int checkThresholds(EvalReport.Summary summary) {
        EvalProperties.Thresholds limits = properties.thresholds();
        List<String> misses = new ArrayList<>();
        if (summary.hybridHitAtK() < limits.hybridHitAtK()) {
            misses.add(
                String.format(
                    Locale.ROOT,
                    "hybrid hit@k %.3f < %.3f",
                    summary.hybridHitAtK(),
                    limits.hybridHitAtK()
                )
            );
        }
        if (summary.refusalRate() < limits.refusalRate()) {
            misses.add(
                String.format(
                    Locale.ROOT,
                    "correct-refusal rate %.3f < %.3f",
                    summary.refusalRate(),
                    limits.refusalRate()
                )
            );
        }
        if (summary.answerAccuracy() < limits.answerAccuracy()) {
            misses.add(
                String.format(
                    Locale.ROOT,
                    "answer accuracy %.3f < %.3f",
                    summary.answerAccuracy(),
                    limits.answerAccuracy()
                )
            );
        }
        if (misses.isEmpty()) {
            log.info("All quality thresholds met.");
            return 0;
        }
        misses.forEach(m -> log.error("THRESHOLD NOT MET: {}", m));
        return 1;
    }
}
