package dev.makris.lexicon.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate-limits /api/**. The client is the authenticated API key (a short digest label) or, when authentication
 * is disabled, the remote address. Behind a reverse proxy, set server.forward-headers-strategy so the
 * remote address reflects the real caller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final RateLimitProperties properties;
    private final MeterRegistry registry;

    public RateLimitFilter(
        RateLimiter limiter,
        RateLimitProperties properties,
        MeterRegistry registry
    ) {
        this.limiter = limiter;
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return (
            !properties.enabled() ||
            !request.getRequestURI().startsWith("/api/")
        );
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        Object authenticated = request.getAttribute(
            ApiKeyFilter.CLIENT_ATTRIBUTE
        );
        String client =
            authenticated != null
                ? "key:" + authenticated
                : "ip:" + request.getRemoteAddr();
        RateLimiter.Tier tier = request
            .getRequestURI()
            .startsWith("/api/v1/answers")
            ? RateLimiter.Tier.ANSWERS
            : RateLimiter.Tier.DEFAULT;

        RateLimiter.Decision decision = limiter.check(client, tier);
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }

        Counter.builder("lexicon.rate_limit.rejected")
            .description("Requests rejected by the rate limiter")
            .tag("tier", tier.name())
            .register(registry)
            .increment();
        response.setHeader(
            "Retry-After",
            String.valueOf(decision.retryAfterSeconds())
        );
        ProblemResponses.write(
            response,
            429,
            "Too Many Requests",
            "Rate limit exceeded. Retry in " +
                decision.retryAfterSeconds() +
                " seconds."
        );
    }
}
