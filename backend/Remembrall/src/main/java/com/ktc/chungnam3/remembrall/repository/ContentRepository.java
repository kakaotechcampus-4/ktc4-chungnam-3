package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.content.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ContentRepository extends JpaRepository<Content, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO content (id, video_id, source_status, analysis_status, created_at, updated_at)
            VALUES (:id, :videoId, 'UNKNOWN', 'PENDING', :now, :now)
            ON CONFLICT (video_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("videoId") String videoId, @Param("now") Instant now);

    @Query("SELECT content.id FROM Content content WHERE content.videoId = :videoId")
    Optional<UUID> findIdByVideoId(@Param("videoId") String videoId);

    @Modifying
    @Query(value = """
            UPDATE content
               SET analysis_status = 'ANALYZING',
                   analysis_started_at = :startedAt,
                   updated_at = :startedAt
             WHERE id = :contentId
               AND analysis_status = 'PENDING'
            """, nativeQuery = true)
    int claimAnalysis(@Param("contentId") UUID contentId, @Param("startedAt") Instant startedAt);

    @Modifying
    @Query(value = """
            UPDATE content
               SET analysis_status = :status,
                   source_status = :sourceStatus,
                   title = COALESCE(:title, title),
                   summary = COALESCE(:summary, summary),
                   category = COALESCE(:category, category),
                   analysis_version = COALESCE(:analysisVersion, analysis_version),
                   last_analysis_error_code = :errorCode,
                   metadata_fetched_at = COALESCE(:metadataFetchedAt, metadata_fetched_at),
                   analyzed_at = :analyzedAt,
                   updated_at = :updatedAt
             WHERE id = :contentId
               AND analysis_status = 'ANALYZING'
            """, nativeQuery = true)
    int applyOutcome(
            @Param("contentId") UUID contentId,
            @Param("status") String status,
            @Param("sourceStatus") String sourceStatus,
            @Param("title") String title,
            @Param("summary") String summary,
            @Param("category") String category,
            @Param("analysisVersion") String analysisVersion,
            @Param("errorCode") String errorCode,
            @Param("metadataFetchedAt") Instant metadataFetchedAt,
            @Param("analyzedAt") Instant analyzedAt,
            @Param("updatedAt") Instant updatedAt
    );
}
