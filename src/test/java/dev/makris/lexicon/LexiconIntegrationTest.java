package dev.makris.lexicon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.makris.lexicon.answer.AnswerGenerator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * End-to-end test over HTTP against a real PostgreSQL + pgvector. Only the two Ollama-backed components are
 * replaced: embeddings by a deterministic hashed bag-of-words, and the chat model by a stub that quotes the
 * first retrieved source. Everything else (Flyway, SQL, hybrid retrieval, grounding, auth, streaming) is real.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "lexicon.security.api-keys=integration-test-key-0123456789",
        "lexicon.answer.min-similarity=0.30",
        "lexicon.embedding.query-prefix=",
        "lexicon.embedding.document-prefix=",
    }
)
class LexiconIntegrationTest {

    private static final String API_KEY = "integration-test-key-0123456789";
    private static final ParameterizedTypeReference<Map<String, Object>> MAP =
        new ParameterizedTypeReference<>() {};

    private static final String VACATION_TEXT =
        "Employees receive 25 days of paid vacation per year.";
    private static final String SUPPORT_TEXT =
        "Customer support answers tickets Monday to Friday from 08:00 to 18:00.";

    private static final GenericContainer<?> POSTGRES = new GenericContainer<>(
        DockerImageName.parse("pgvector/pgvector:pg17")
    )
        .withEnv("POSTGRES_DB", "lexicon")
        .withEnv("POSTGRES_USER", "lexicon")
        .withEnv("POSTGRES_PASSWORD", "lexicon")
        .withExposedPorts(5432)
        .waitingFor(
            Wait.forLogMessage(
                ".*database system is ready to accept connections.*\\s",
                2
            )
        );

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add(
            "spring.datasource.url",
            () ->
                "jdbc:postgresql://" +
                POSTGRES.getHost() +
                ":" +
                POSTGRES.getMappedPort(5432) +
                "/lexicon"
        );
        registry.add("spring.datasource.username", () -> "lexicon");
        registry.add("spring.datasource.password", () -> "lexicon");
    }

    @TestConfiguration
    static class Fakes {

        @Bean
        @Primary
        EmbeddingModel fakeEmbeddingModel() {
            EmbeddingModel model = mock(EmbeddingModel.class);
            when(model.embed(anyString())).thenAnswer(call ->
                embedding(call.getArgument(0))
            );
            when(model.embed(anyList())).thenAnswer(call ->
                ((List<?>) call.getArgument(0))
                    .stream()
                    .map(text -> embedding((String) text))
                    .toList()
            );
            return model;
        }

        @Bean
        @Primary
        AnswerGenerator fakeAnswerGenerator() {
            return (system, user) -> firstSourceSentence(user);
        }
    }

    @Value("${local.server.port}")
    private int port;

    private RestClient api;

    private RestClient anonymous;

    @BeforeEach
    void setUp() {
        api = client(true);
        anonymous = client(false);
        // Uploads are idempotent, so repeating them for every test is cheap and keeps tests independent.
        assertThat(
            upload("vacation.txt", VACATION_TEXT)
                .getStatusCode()
                .is2xxSuccessful()
        ).isTrue();
        assertThat(
            upload("support.txt", SUPPORT_TEXT)
                .getStatusCode()
                .is2xxSuccessful()
        ).isTrue();
    }

    @Test
    void requestsWithoutAnApiKeyAreRejected() {
        ResponseEntity<Map<String, Object>> response = anonymous
            .get()
            .uri("/api/v1/documents")
            .retrieve()
            .toEntity(MAP);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
            response.getHeaders().getContentType().toString()
        ).startsWith("application/problem+json");
    }

    @Test
    void uploadingTheSameFileTwiceIsIdempotent() {
        String text =
            "Unique idempotency probe " +
            System.nanoTime() +
            " lorem ipsum dolor sit amet.";

        ResponseEntity<Map<String, Object>> first = upload("probe.txt", text);
        ResponseEntity<Map<String, Object>> second = upload("probe.txt", text);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getBody()).containsEntry("status", "READY");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody()).containsEntry(
            "id",
            first.getBody().get("id")
        );
    }

    @Test
    void unsupportedFileTypesAreRejected() {
        ResponseEntity<Map<String, Object>> response = upload(
            "malware.exe",
            "not a document"
        );

        assertThat(response.getStatusCode()).isEqualTo(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void hybridSearchRanksTheRelevantDocumentFirst() {
        ResponseEntity<Map<String, Object>> response = post(
            "/api/v1/search",
            Map.of(
                "query",
                "How many vacation days do employees get?",
                "mode",
                "HYBRID",
                "topK",
                3
            )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> results = (List<Map<String, Object>>) response
            .getBody()
            .get("results");
        assertThat(results).isNotEmpty();
        assertThat(results.get(0)).containsEntry("filename", "vacation.txt");
        assertThat(results.get(0).get("vectorRank")).isEqualTo(1);
        assertThat(results.get(0).get("keywordRank")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aGroundedQuestionIsAnsweredWithAVerifiedCitation() {
        ResponseEntity<Map<String, Object>> response = post(
            "/api/v1/answers",
            Map.of("question", "How many vacation days do employees get?")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).containsEntry("status", "ANSWERED");
        assertThat((String) body.get("answer"))
            .contains("25 days")
            .contains("[1]");
        List<Map<String, Object>> citations = (List<
            Map<String, Object>
        >) body.get("citations");
        assertThat(citations).hasSize(1);
        assertThat(citations.get(0)).containsEntry("filename", "vacation.txt");
    }

    @Test
    void anUnrelatedQuestionIsRefusedWithoutCallingTheModel() {
        ResponseEntity<Map<String, Object>> response = post(
            "/api/v1/answers",
            Map.of("question", "zebra marmalade")
        );

        assertThat(response.getBody()).containsEntry("status", "NO_ANSWER");
        assertThat(response.getBody()).containsEntry(
            "reason",
            "NO_RELEVANT_CONTEXT"
        );
    }

    @Test
    void theStreamingEndpointEndsWithAnAuthoritativeResultEvent() {
        String stream = api
            .post()
            .uri("/api/v1/answers/stream")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .body(
                Map.of("question", "How many vacation days do employees get?")
            )
            .retrieve()
            .body(String.class);

        assertThat(stream)
            .contains("event:token")
            .contains("event:result")
            .contains("\"status\":\"ANSWERED\"");
    }

    @Test
    void deletingADocumentRemovesIt() {
        String text =
            "Disposable document " +
            System.nanoTime() +
            " that will be deleted again.";
        Object id = upload("disposable.txt", text).getBody().get("id");

        ResponseEntity<Void> deleted = api
            .delete()
            .uri("/api/v1/documents/{id}", id)
            .retrieve()
            .toBodilessEntity();
        ResponseEntity<Map<String, Object>> after = api
            .get()
            .uri("/api/v1/documents/{id}", id)
            .retrieve()
            .toEntity(MAP);

        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- helpers ----

    private RestClient client(boolean authenticated) {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            // Return error responses instead of throwing, so tests can assert on status codes.
            .defaultStatusHandler(
                HttpStatusCode::isError,
                (request, response) -> {}
            );
        if (authenticated) {
            builder.defaultHeader("X-Api-Key", API_KEY);
        }
        return builder.build();
    }

    private ResponseEntity<Map<String, Object>> upload(
        String filename,
        String text
    ) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add(
            "file",
            new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }
        );
        return api
            .post()
            .uri("/api/v1/documents")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(parts)
            .retrieve()
            .toEntity(MAP);
    }

    private ResponseEntity<Map<String, Object>> post(
        String uri,
        Map<String, Object> body
    ) {
        return api
            .post()
            .uri(uri)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(MAP);
    }

    /** Deterministic stand-in for an embedding model: hashed bag of words, L2-normalised, 768 dimensions. */
    private static float[] embedding(String text) {
        float[] vector = new float[768];
        for (String token : text
            .toLowerCase(Locale.ROOT)
            .split("[^\\p{L}\\p{N}]+")) {
            if (token.length() >= 3) {
                vector[Math.floorMod(token.hashCode(), vector.length)] += 1f;
            }
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            vector[0] = 1f;
            return vector;
        }
        float scale = (float) (1 / Math.sqrt(norm));
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= scale;
        }
        return vector;
    }

    /** Stand-in for the chat model: quotes the first sentence of source [1], or declines if there is none. */
    private static String firstSourceSentence(String userPrompt) {
        Matcher matcher = Pattern.compile(
            "(?s)<sources>\\s*\\[1\\] \\([^)]*\\)\\n(.*?)\\n\\n"
        ).matcher(userPrompt);
        if (!matcher.find()) {
            return "NOT_FOUND";
        }
        return matcher.group(1).split("(?<=[.!?])\\s+")[0] + " [1]";
    }
}
