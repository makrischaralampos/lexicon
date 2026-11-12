package dev.makris.lexicon.api;

import dev.makris.lexicon.common.PageResponse;
import dev.makris.lexicon.document.DocumentService;
import dev.makris.lexicon.ingestion.IngestionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Documents")
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final IngestionService ingestion;
    private final DocumentService documents;

    public DocumentController(
        IngestionService ingestion,
        DocumentService documents
    ) {
        this.ingestion = ingestion;
        this.documents = documents;
    }

    /** 201 for a newly ingested document, 200 if an identical file was already uploaded. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> upload(
        @RequestParam("file") MultipartFile file
    ) throws IOException {
        String filename = StringUtils.getFilename(
            StringUtils.cleanPath(
                file.getOriginalFilename() == null
                    ? "unnamed"
                    : file.getOriginalFilename()
            )
        );
        IngestionService.IngestionResult result = ingestion.ingest(
            filename == null ? "unnamed" : filename,
            file.getBytes()
        );

        DocumentResponse body = DocumentResponse.from(result.document());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(body.id())
            .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    public PageResponse<DocumentResponse> list(
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return documents.list(page, size).map(DocumentResponse::from);
    }

    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable UUID id) {
        return DocumentResponse.from(documents.get(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documents.delete(id);
        return ResponseEntity.noContent().build();
    }
}
