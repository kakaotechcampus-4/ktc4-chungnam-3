package com.ktc.chungnam3.remembrall.content.embedding;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
                "project", "us-central1", "gemini-embedding-001", 768, Duration.ofSeconds(10));

        assertThat(client.configFor(EmbeddingClient.TaskType.RETRIEVAL_DOCUMENT).taskType())
                .hasValue("RETRIEVAL_DOCUMENT");
        assertThat(client.configFor(EmbeddingClient.TaskType.RETRIEVAL_QUERY).taskType())
                .hasValue("RETRIEVAL_QUERY");
        assertThat(client.configFor(EmbeddingClient.TaskType.RETRIEVAL_DOCUMENT).outputDimensionality())
                .hasValue(768);
        assertThat(client.configFor(EmbeddingClient.TaskType.RETRIEVAL_DOCUMENT).httpOptions()
                .orElseThrow().timeout()).hasValue(10_000);
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

    @Test
    void createsSdkClientOnceReusesItAcrossTasksAndClosesAtApplicationShutdown() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requests = new CopyOnWriteArrayList<String>();
        server.createContext("/", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"predictions\":[{\"embeddings\":{\"values\":[3.0,4.0]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            Client sdk = spy(Client.builder().vertexAI(true).project("test-project").location("us-central1")
                    .credentials(GoogleCredentials.create(new AccessToken("test-token",
                            Date.from(Instant.parse("2099-01-01T00:00:00Z")))))
                    .httpOptions(HttpOptions.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build())
                    .build());
            Client.Builder builder = mock(Client.Builder.class, RETURNS_SELF);
            when(builder.build()).thenReturn(sdk);
            try (var factory = mockStatic(Client.class)) {
                factory.when(Client::builder).thenReturn(builder);
                try (var context = new AnnotationConfigApplicationContext()) {
                    context.registerBean(VertexEmbeddingClient.class, () -> new VertexEmbeddingClient(
                            "project", "us-central1", "gemini-embedding-001", 2, Duration.ofSeconds(10)));
                    context.refresh();
                    var client = context.getBean(VertexEmbeddingClient.class);

                    assertThat(client.embedDocument("document").vector()).containsExactly(0.6f, 0.8f);
                    assertThat(client.embedQuery("query").vector()).containsExactly(0.6f, 0.8f);
                    factory.verify(Client::builder, times(1));
                    verify(builder, times(1)).build();
                    verify(sdk, never()).close();
                }
                verify(sdk, times(1)).close();
            }
            assertThat(requests).hasSize(2);
            assertThat(requests.getFirst()).contains("RETRIEVAL_DOCUMENT");
            assertThat(requests.getLast()).contains("RETRIEVAL_QUERY");
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
            var client = new VertexEmbeddingClient("project", "us-central1", "model", 2, Duration.ofSeconds(10));

            assertThatThrownBy(() -> client.embedQuery("first")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> client.embedDocument("second")).isInstanceOf(IllegalStateException.class);
            client.close();

            factory.verify(Client::builder, times(1));
            verify(builder, times(1)).build();
        }
    }
}
