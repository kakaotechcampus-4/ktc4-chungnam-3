package com.ktc.chungnam3.remembrall.content.dto;

import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;

import java.time.Instant;
import java.util.Objects;

public record AnalysisOutcome(
        ContentAnalysisStatus status,
        ContentSourceStatus sourceStatus,
        String title,
        String summary,
        String category,
        String analysisVersion,
        String errorCode,
        Instant metadataFetchedAt
) {
    public AnalysisOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(sourceStatus, "sourceStatus");
        if (!status.isTerminal()) {
            throw new IllegalArgumentException("Analysis outcome must have a terminal status");
        }
        if (status == ContentAnalysisStatus.COMPLETED && errorCode != null) {
            throw new IllegalArgumentException("Completed analysis cannot have an error code");
        }
        if (status != ContentAnalysisStatus.COMPLETED && (errorCode == null || errorCode.isBlank())) {
            throw new IllegalArgumentException("Partial or failed analysis requires an error code");
        }
        if (title != null && title.length() > 500) {
            throw new IllegalArgumentException("title exceeds 500 characters");
        }
        if (category != null && category.length() > 50) {
            throw new IllegalArgumentException("category exceeds 50 characters");
        }
        if (analysisVersion != null && analysisVersion.length() > 30) {
            throw new IllegalArgumentException("analysisVersion exceeds 30 characters");
        }
        if (errorCode != null && errorCode.length() > 50) {
            throw new IllegalArgumentException("errorCode exceeds 50 characters");
        }
    }
}
