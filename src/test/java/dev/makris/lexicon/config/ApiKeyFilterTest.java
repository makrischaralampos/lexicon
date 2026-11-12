package dev.makris.lexicon.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiKeyFilterTest {

    private static final String KEY = "0123456789abcdef0123456789abcdef";

    private final ApiKeyFilter filter = new ApiKeyFilter(
        new SecurityProperties(List.of(KEY))
    );

    private MockHttpServletResponse call(
        ApiKeyFilter filter,
        MockHttpServletRequest request,
        MockFilterChain chain
    ) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void validKeyPassesAndLabelsTheClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET",
            "/api/v1/documents"
        );
        request.addHeader(ApiKeyFilter.HEADER, KEY);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = call(filter, request, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(
            (String) request.getAttribute(ApiKeyFilter.CLIENT_ATTRIBUTE)
        ).hasSize(12);
    }

    @Test
    void missingKeyIsRejectedWithProblemJson() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = call(
            filter,
            new MockHttpServletRequest("GET", "/api/v1/documents"),
            chain
        );

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(
            "application/problem+json"
        );
        assertThat(response.getContentAsString()).contains("\"status\":401");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void wrongKeyIsRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
            "GET",
            "/api/v1/documents"
        );
        request.addHeader(ApiKeyFilter.HEADER, "wrong-key-wrong-key-wrong-key");
        MockFilterChain chain = new MockFilterChain();

        assertThat(call(filter, request, chain).getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void pathsOutsideApiAreNotProtected() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = call(
            filter,
            new MockHttpServletRequest("GET", "/actuator/health"),
            chain
        );

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void withoutConfiguredKeysAuthenticationIsDisabled() throws Exception {
        ApiKeyFilter open = new ApiKeyFilter(new SecurityProperties(List.of()));
        MockFilterChain chain = new MockFilterChain();

        call(
            open,
            new MockHttpServletRequest("GET", "/api/v1/documents"),
            chain
        );

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shortKeysAreRefusedAtStartup() {
        assertThatThrownBy(() ->
            new ApiKeyFilter(new SecurityProperties(List.of("short")))
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 16");
    }
}
