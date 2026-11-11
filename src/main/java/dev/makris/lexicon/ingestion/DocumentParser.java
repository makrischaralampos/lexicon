package dev.makris.lexicon.ingestion;

import java.util.List;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Extracts text from uploaded files. PDFs are read page by page so chunks can cite page numbers. */
@Component
public class DocumentParser {

    public record PageText(Integer pageNumber, String text) {}

    public List<PageText> parse(
        SupportedType type,
        String filename,
        byte[] content
    ) {
        Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        if (type == SupportedType.PDF) {
            List<Document> pages = new PagePdfDocumentReader(resource).get();
            return java.util.stream.IntStream.range(0, pages.size())
                .mapToObj(i ->
                    new PageText(
                        pageNumber(pages.get(i), i),
                        clean(pages.get(i).getText())
                    )
                )
                .toList();
        }

        return new TikaDocumentReader(resource)
            .get()
            .stream()
            .map(doc -> new PageText(null, clean(doc.getText())))
            .toList();
    }

    private static Integer pageNumber(Document page, int index) {
        Object value = page.getMetadata().get("page_number");
        return value instanceof Number number ? number.intValue() : index + 1;
    }

    /** PostgreSQL text columns cannot contain NUL characters. */
    private static String clean(String text) {
        return text == null ? "" : text.replace('\0', ' ');
    }
}
