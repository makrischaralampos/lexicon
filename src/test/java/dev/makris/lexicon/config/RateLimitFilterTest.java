package dev.makris.lexicon.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RateLimitProperties properties = new RateLimitProperties(
        true,
        120,
        1
    );
    private final RateLimitFilter filter = new RateLimitFilter(
        new RateLimiter(properties),
        properties,
        registry
    );

    private MockHttpServletResponse call(String path, MockFilterChain chain)
        throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(
            new MockHttpServletRequest("POST", path),
            response,
            chain
        );
        return response;
    }

    @Test
    void secondRequestOverTheLimitGets429WithRetryAfter() throws Exception {
        MockFilterChain first = new MockFilterChain();
        assertThat(call("/api/v1/answers", first).getStatus()).isEqualTo(200);
        assertThat(first.getRequest()).isNotNull();

        MockFilterChain second = new MockFilterChain();
        MockHttpServletResponse rejected = call("/api/v1/answers", second);

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isNotNull();
        assertThat(rejected.getContentType()).startsWith(
            "application/problem+json"
        );
        assertThat(second.getRequest()).isNull();
        assertThat(
            registry
                .get("lexicon.rate_limit.rejected")
                .tag("tier", "ANSWERS")
                .counter()
                .count()
        ).isEqualTo(1.0);
    }

    @Test
    void nonApiPathsAreNeverLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            MockFilterChain chain = new MockFilterChain();
            assertThat(call("/actuator/health", chain).getStatus()).isEqualTo(
                200
            );
            assertThat(chain.getRequest()).isNotNull();
        }
    }
}
