package dev.makris.lexicon.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless API-key authentication for /api/**. Only SHA-256 digests of the configured keys are kept, and
 * digests are compared in constant time. Actuator, Swagger UI and the OpenAPI document are not under /api.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    /** Request attribute holding a short, non-reversible label of the authenticated key. */
    public static final String CLIENT_ATTRIBUTE = "lexicon.client";

    private static final int MIN_KEY_LENGTH = 16;
    private static final Logger log = LoggerFactory.getLogger(
        ApiKeyFilter.class
    );

    private final List<byte[]> keyDigests;

    public ApiKeyFilter(SecurityProperties properties) {
        List<String> keys = properties
            .apiKeys()
            .stream()
            .map(String::strip)
            .filter(k -> !k.isEmpty())
            .toList();
        for (String key : keys) {
            if (key.length() < MIN_KEY_LENGTH) {
                throw new IllegalStateException(
                    "API keys must be at least " +
                        MIN_KEY_LENGTH +
                        " characters long (try: openssl rand -hex 24)"
                );
            }
        }
        this.keyDigests = keys.stream().map(ApiKeyFilter::sha256).toList();
        if (keyDigests.isEmpty()) {
            log.warn(
                "No API keys configured (lexicon.security.api-keys): authentication is DISABLED."
            );
        } else {
            log.info(
                "API-key authentication enabled ({} key(s)).",
                keyDigests.size()
            );
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return (
            keyDigests.isEmpty() || !request.getRequestURI().startsWith("/api/")
        );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        String presented = request.getHeader(HEADER);
        if (presented != null && !presented.isBlank()) {
            byte[] digest = sha256(presented.strip());
            for (byte[] known : keyDigests) {
                if (MessageDigest.isEqual(known, digest)) {
                    request.setAttribute(
                        CLIENT_ATTRIBUTE,
                        HexFormat.of().formatHex(digest, 0, 6)
                    );
                    chain.doFilter(request, response);
                    return;
                }
            }
        }
        ProblemResponses.write(
            response,
            401,
            "Unauthorized",
            "A valid API key is required in the " + HEADER + " header."
        );
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
