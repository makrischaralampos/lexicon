package dev.makris.lexicon.config;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Writes RFC 9457 problem+json responses from servlet filters, where controller advice does not apply. */
final class ProblemResponses {

    private ProblemResponses() {}

    static void write(
        HttpServletResponse response,
        int status,
        String title,
        String detail
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response
            .getWriter()
            .write(
                "{\"type\":\"about:blank\",\"title\":\"" +
                    escape(title) +
                    "\",\"status\":" +
                    status +
                    ",\"detail\":\"" +
                    escape(detail) +
                    "\"}"
            );
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
