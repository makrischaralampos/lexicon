package dev.makris.lexicon.api;

import dev.makris.lexicon.answer.AnswerEvent;
import dev.makris.lexicon.answer.AnswerResult;
import dev.makris.lexicon.answer.AnswerService;
import dev.makris.lexicon.common.ApiException;
import dev.makris.lexicon.retrieval.SearchMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "Answers")
@RestController
@RequestMapping("/api/v1/answers")
public class AnswerController {

    private static final Logger log = LoggerFactory.getLogger(
        AnswerController.class
    );

    /** Sent as the payload of an SSE "error" event. */
    public record StreamError(int status, String detail) {}

    private final AnswerService answers;

    public AnswerController(AnswerService answers) {
        this.answers = answers;
    }

    @Operation(
        summary = "Answer a question from the uploaded documents, with citations"
    )
    @PostMapping
    public AnswerResult answer(@Valid @RequestBody AnswerRequest request) {
        return answers.answer(
            request.question(),
            modeOf(request),
            request.topK(),
            request.documentIds()
        );
    }

    @Operation(
        summary = "Streaming variant of the answer endpoint (Server-Sent Events)",
        description = "Emits 'token' events with provisional text, then one 'result' event with the " +
            "validated answer and citations. A failure after the stream has started is reported " +
            "as an 'error' event."
    )
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(
        @Valid @RequestBody AnswerRequest request
    ) {
        return answers
            .stream(
                request.question(),
                modeOf(request),
                request.topK(),
                request.documentIds()
            )
            .map(AnswerController::toSse)
            .onErrorResume(AnswerController::toErrorEvent);
    }

    private static SearchMode modeOf(AnswerRequest request) {
        return request.mode() == null ? SearchMode.HYBRID : request.mode();
    }

    private static ServerSentEvent<Object> toSse(AnswerEvent event) {
        return switch (event) {
            case AnswerEvent.Token token -> ServerSentEvent.<Object>builder(
                Map.of("text", token.text())
            )
                .event("token")
                .build();
            case AnswerEvent.Result result -> ServerSentEvent.<Object>builder(
                result.result()
            )
                .event("result")
                .build();
        };
    }

    private static Flux<ServerSentEvent<Object>> toErrorEvent(Throwable ex) {
        StreamError error;
        if (ex instanceof ApiException api) {
            error = new StreamError(api.status().value(), api.getMessage());
        } else {
            log.error("Streaming answer failed", ex);
            error = new StreamError(500, "An unexpected error occurred.");
        }
        return Flux.just(
            ServerSentEvent.<Object>builder(error).event("error").build()
        );
    }
}
