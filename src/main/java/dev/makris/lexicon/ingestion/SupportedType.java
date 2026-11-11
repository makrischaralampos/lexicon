package dev.makris.lexicon.ingestion;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum SupportedType {
    PDF("application/pdf", "pdf"),
    DOCX(
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "docx"
    ),
    MARKDOWN("text/markdown", "md", "markdown"),
    TEXT("text/plain", "txt");

    private final String contentType;
    private final Set<String> extensions;

    SupportedType(String contentType, String... extensions) {
        this.contentType = contentType;
        this.extensions = Set.of(extensions);
    }

    public String contentType() {
        return contentType;
    }

    public static Optional<SupportedType> fromFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (SupportedType type : values()) {
            if (type.extensions.contains(extension)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
