package com.ktc.chungnam3.remembrall.content.embedding;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
