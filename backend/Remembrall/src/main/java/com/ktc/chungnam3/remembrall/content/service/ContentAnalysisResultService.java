package com.ktc.chungnam3.remembrall.content.service;

import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.embedding.EmbeddingClient;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ContentAnalysisResultService {

    private static final Logger log = LoggerFactory.getLogger(ContentAnalysisResultService.class);
    private static final int STORED_DIMENSIONS = 768;

    private final ContentPersistenceService contentPersistenceService;
    private final EmbeddingClient embeddingClient;

    public ContentAnalysisResultService(ContentPersistenceService contentPersistenceService, EmbeddingClient embeddingClient) {
        this.contentPersistenceService = contentPersistenceService;
        this.embeddingClient = embeddingClient;
    }

    public boolean applyOutcome(UUID contentId, AnalysisOutcome outcome) {
        EmbeddingClient.Result embedding = null;
        if (outcome.status() != ContentAnalysisStatus.FAILED
                && outcome.summary() != null && !outcome.summary().isBlank()) {
            try {
                embedding = embeddingClient.embedDocument(embeddingText(outcome));
                validate(embedding);
            }
            catch (RuntimeException e) {
                log.warn("Could not embed analysis result for content {}", contentId, e);
                embedding = null;
            }
        }
        return contentPersistenceService.applyOutcome(contentId, outcome, embedding);
    }

    private static String embeddingText(AnalysisOutcome outcome) {
        List<String> parts = new ArrayList<>(3);
        addIfPresent(parts, "제목", outcome.title());
        addIfPresent(parts, "요약", outcome.summary());
        addIfPresent(parts, "카테고리", outcome.category());
        return String.join("\n", parts);
    }

    private static void addIfPresent(List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(label + ": " + value.strip());
        }
    }

    private static void validate(EmbeddingClient.Result embedding) {
        if (embedding == null || embedding.vector() == null || embedding.vector().length != STORED_DIMENSIONS
                || embedding.model() == null || embedding.model().isBlank()) {
            throw new IllegalStateException("Invalid content embedding");
        }
        for (float value : embedding.vector()) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Content embedding contains a non-finite value");
            }
        }
    }
}
