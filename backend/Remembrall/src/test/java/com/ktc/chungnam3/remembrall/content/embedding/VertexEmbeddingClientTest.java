package com.ktc.chungnam3.remembrall.content.embedding;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VertexEmbeddingClientTest {

    @Test
    void configuresBothRetrievalTasksWithDimensionsAndTimeout() {
        VertexEmbeddingClient client = new VertexEmbeddingClient(
                false, "test-api-key", "", "", "gemini-embedding-001", 768, Duration.ofSeconds(10));
        try {
            assertThat(client.configFor(EmbeddingClient.TaskType.RETRIEVAL_DOCUMENT).taskType())
                    .hasValue("RETRIEVAL_DOCUMENT");
            assertThat(client.configFor(EmbeddingClient.TaskType.RETRIEVAL_QUERY).taskType())
                    .hasValue("RETRIEVAL_QUERY");
            assertThat(client.configFor(EmbeddingClient.TaskType.RETRIEVAL_DOCUMENT).outputDimensionality())
                    .hasValue(768);
            assertThat(client.configFor(EmbeddingClient.TaskType.RETRIEVAL_DOCUMENT).httpOptions()
                    .orElseThrow().timeout()).hasValue(10_000);
        } finally {
            client.close();
        }
    }

    @Test
    void defaultsToApiKeyModeWithoutVertexProjectOrLocation() {
        Client sdk = mock(Client.class);
        Client.Builder builder = mock(Client.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(sdk);
        try (var factory = mockStatic(Client.class)) {
            factory.when(Client::builder).thenReturn(builder);
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("embedding-test",
                        Map.of("spring.ai.google.genai.api-key", "test-api-key")));
                context.register(VertexEmbeddingClient.class);
                context.refresh();

                verify(builder).vertexAI(false);
                verify(builder).apiKey("test-api-key");
                verify(builder, never()).project(anyString());
                verify(builder, never()).location(anyString());
            }
            verify(sdk).close();
        }
    }

    @Test
    void normalizesAndRejectsMalformedVectors() {
        assertThat(VertexEmbeddingClient.normalize(List.of(3f, 4f), 2))
                .containsExactly(0.6f, 0.8f);
        assertThatThrownBy(() -> VertexEmbeddingClient.normalize(List.of(1f), 2))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> VertexEmbeddingClient.normalize(List.of(0f, 0f), 2))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void createsSdkClientOnceReusesItAcrossTasksAndClosesAtApplicationShutdown(boolean vertexAi) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new CopyOnWriteArrayList<String>();
        var paths = new CopyOnWriteArrayList<String>();
        var apiKeys = new CopyOnWriteArrayList<String>();
        server.createContext("/", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            paths.add(exchange.getRequestURI().getPath());
            apiKeys.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            String payload = vertexAi ? "{\"predictions\":[{\"embeddings\":{\"values\":[3.0,4.0]}}]}"
                    : "{\"embeddings\":[{\"values\":[3.0,4.0]}]}";
            byte[] response = payload.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            Client.Builder sdkBuilder = Client.builder().vertexAI(vertexAi)
                    .httpOptions(HttpOptions.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build());
            if (vertexAi) {
                sdkBuilder.project("test-project").location("us-central1")
                        .credentials(GoogleCredentials.create(new AccessToken("test-token",
                                Date.from(Instant.parse("2099-01-01T00:00:00Z")))));
            } else {
                sdkBuilder.apiKey("test-api-key");
            }
            Client sdk = spy(sdkBuilder.build());
            Client.Builder builder = mock(Client.Builder.class, RETURNS_SELF);
            when(builder.build()).thenReturn(sdk);
            try (var factory = mockStatic(Client.class)) {
                factory.when(Client::builder).thenReturn(builder);
                try (var context = new AnnotationConfigApplicationContext()) {
                    context.registerBean(VertexEmbeddingClient.class, () -> new VertexEmbeddingClient(
                            vertexAi, "test-api-key", "project", "us-central1", "gemini-embedding-001", 2, Duration.ofSeconds(10)));
                    context.refresh();
                    var client = context.getBean(VertexEmbeddingClient.class);

                    assertThat(client.embedDocument("document").vector()).containsExactly(0.6f, 0.8f);
                    assertThat(client.embedQuery("query").vector()).containsExactly(0.6f, 0.8f);
                    factory.verify(Client::builder, times(1));
                    verify(builder, times(1)).build();
                    verify(builder).vertexAI(vertexAi);
                    if (vertexAi) {
                        verify(builder).project("project");
                        verify(builder).location("us-central1");
                        verify(builder, never()).apiKey(anyString());
                    } else {
                        verify(builder).apiKey("test-api-key");
                        verify(builder, never()).project(anyString());
                        verify(builder, never()).location(anyString());
                    }
                    verify(sdk, never()).close();
                }
                verify(sdk, times(1)).close();
            }
            assertThat(requests).hasSize(2);
            assertThat(requests.getFirst()).contains("RETRIEVAL_DOCUMENT");
            assertThat(requests.getLast()).contains("RETRIEVAL_QUERY");
            assertThat(paths).allSatisfy(path -> assertThat(path)
                    .endsWith(vertexAi ? "gemini-embedding-001:predict" : "gemini-embedding-001:batchEmbedContents"));
            if (!vertexAi) {
                assertThat(apiKeys).containsExactly("test-api-key", "test-api-key");
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingCredentialsDoNotBreakStartupAndAreNotRetriedPerCall() {
        Client.Builder builder = mock(Client.Builder.class, RETURNS_SELF);
        when(builder.build()).thenThrow(new IllegalArgumentException("Missing configuration"));
        try (var factory = mockStatic(Client.class)) {
            factory.when(Client::builder).thenReturn(builder);
            var client = new VertexEmbeddingClient(true, "", "project", "us-central1", "model", 2, Duration.ofSeconds(10));

            assertThatThrownBy(() -> client.embedQuery("first")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> client.embedDocument("second")).isInstanceOf(IllegalStateException.class);
            client.close();

            factory.verify(Client::builder, times(1));
            verify(builder, times(1)).build();
        }
    }

    @Test
    void missingApiKeyDoesNotFallBackToVertexCredentials() {
        try (var factory = mockStatic(Client.class)) {
            var client = new VertexEmbeddingClient(false, " ", "project", "us-central1", "model", 2, Duration.ofSeconds(10));

            assertThatThrownBy(() -> client.embedDocument("document"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class);
            factory.verifyNoInteractions();
            client.close();
        }
    }
}
