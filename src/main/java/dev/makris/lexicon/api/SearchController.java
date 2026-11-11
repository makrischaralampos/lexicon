package dev.makris.lexicon.api;

import dev.makris.lexicon.retrieval.RetrievalService;
import dev.makris.lexicon.retrieval.RetrievedChunk;
import dev.makris.lexicon.retrieval.SearchMode;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final RetrievalService retrieval;

    public SearchController(RetrievalService retrieval) {
        this.retrieval = retrieval;
    }

    @PostMapping
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        long startedAt = System.nanoTime();
        SearchMode mode =
            request.mode() == null ? SearchMode.HYBRID : request.mode();
        int topK = retrieval.effectiveTopK(request.topK());

        List<RetrievedChunk> chunks = retrieval.retrieve(
            request.query(),
            mode,
            topK,
            request.documentIds()
        );

        long tookMs = (System.nanoTime() - startedAt) / 1_000_000;
        return SearchResponse.of(request.query(), mode, topK, tookMs, chunks);
    }
}
