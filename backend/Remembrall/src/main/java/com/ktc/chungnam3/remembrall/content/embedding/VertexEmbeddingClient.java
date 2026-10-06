package com.ktc.chungnam3.remembrall.content.embedding;

import com.google.genai.Client;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.HttpOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class VertexEmbeddingClient implements EmbeddingClient {

    private final String projectId;
    private final String location;
    private final String model;
    private final int dimensions;
    private final int timeoutMillis;

    public VertexEmbeddingClient(
            @Value("${spring.ai.google.genai.project-id:}") String projectId,
            @Value("${spring.ai.google.genai.location:us-central1}") String location,
            @Value("${content.embedding.model:gemini-embedding-001}") String model,
            @Value("${content.embedding.dimensions:768}") int dimensions,
            @Value("${content.embedding.timeout:10s}") Duration timeout
    ) {
        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.dimensions = dimensions;
        this.timeoutMillis = Math.toIntExact(timeout.toMillis());
        if (dimensions <= 0 || timeoutMillis <= 0) {
            throw new IllegalArgumentException("Embedding dimensions and timeout must be positive");
        }
    }

    @Override
    public Result embed(String text, TaskType taskType) {
        // The existing Vertex configuration uses ADC; the SDK resolves the same credentials.
        try (Client client = Client.builder()
                .vertexAI(true)
                .project(projectId)
                .location(location)
                .build()) {
            List<ContentEmbedding> embeddings = client.models.embedContent(model, text, configFor(taskType))
                    .embeddings().orElseThrow(() -> new IllegalStateException("Missing embedding response"));
            if (embeddings.size() != 1) {
                throw new IllegalStateException("Expected one embedding, got " + embeddings.size());
            }
            List<Float> values = embeddings.getFirst().values()
                    .orElseThrow(() -> new IllegalStateException("Missing embedding values"));
            return new Result(normalize(values, dimensions), model);
        }
    }

    EmbedContentConfig configFor(TaskType taskType) {
        return EmbedContentConfig.builder()
                .taskType(taskType.name())
                .outputDimensionality(dimensions)
                .httpOptions(HttpOptions.builder().timeout(timeoutMillis).build())
                .build();
    }

    static float[] normalize(List<Float> values, int dimensions) {
        if (values.size() != dimensions) {
            throw new IllegalStateException("Expected " + dimensions + " dimensions, got " + values.size());
        }
        double squaredLength = 0;
        for (Float value : values) {
            if (value == null || !Float.isFinite(value)) {
                throw new IllegalStateException("Embedding contains a non-finite value");
            }
            squaredLength += (double) value * value;
        }
        double length = Math.sqrt(squaredLength);
        if (!Double.isFinite(length) || length == 0) {
            throw new IllegalStateException("Embedding has no finite length");
        }
        float[] normalized = new float[dimensions];
        for (int i = 0; i < dimensions; i++) {
            normalized[i] = (float) (values.get(i) / length);
        }
        return normalized;
    }
}
