package dev.makris.lexicon.api;

import dev.makris.lexicon.retrieval.SearchMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record SearchRequest(
    @NotBlank @Size(max = 1000) String query,
    @Min(1) Integer topK,
    SearchMode mode,
    @Size(max = 50) List<UUID> documentIds
) {}
