package dev.makris.lexicon.document;

import dev.makris.lexicon.common.ApiException;
import dev.makris.lexicon.common.PageResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    private final DocumentRepository documents;

    public DocumentService(DocumentRepository documents) {
        this.documents = documents;
    }

    public StoredDocument get(UUID id) {
        return documents
            .findById(id)
            .orElseThrow(() ->
                ApiException.notFound("Document " + id + " was not found.")
            );
    }

    public PageResponse<StoredDocument> list(int page, int size) {
        return new PageResponse<>(
            documents.findAll(size, page * size),
            page,
            size,
            documents.count()
        );
    }

    public void delete(UUID id) {
        if (!documents.delete(id)) {
            throw ApiException.notFound("Document " + id + " was not found.");
        }
    }
}
