package dev.makris.lexicon.api;

import dev.makris.lexicon.answer.AnswerResult;
import dev.makris.lexicon.answer.AnswerService;
import dev.makris.lexicon.retrieval.SearchMode;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private final AnswerService answers;

    public AnswerController(AnswerService answers) {
        this.answers = answers;
    }

    @PostMapping
    public AnswerResult answer(@Valid @RequestBody AnswerRequest request) {
        SearchMode mode =
            request.mode() == null ? SearchMode.HYBRID : request.mode();
        return answers.answer(
            request.question(),
            mode,
            request.topK(),
            request.documentIds()
        );
    }
}
