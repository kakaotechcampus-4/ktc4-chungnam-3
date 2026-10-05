package com.ktc.chungnam3.remembrall.domain.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "content", uniqueConstraints = @UniqueConstraint(name = "uk_content_video_id", columnNames = "video_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content {

    @Id
    private UUID id;

    @Column(name = "video_id", nullable = false, length = 32)
    private String videoId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "category", length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_status", nullable = false, length = 20)
    private ContentSourceStatus sourceStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status", nullable = false, length = 20)
    private ContentAnalysisStatus analysisStatus;

    @Column(name = "analysis_version", length = 30)
    private String analysisVersion;

    @Column(name = "last_analysis_error_code", length = 50)
    private String lastAnalysisErrorCode;

    @Column(name = "metadata_fetched_at")
    private Instant metadataFetchedAt;

    @Column(name = "analysis_started_at")
    private Instant analysisStartedAt;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
