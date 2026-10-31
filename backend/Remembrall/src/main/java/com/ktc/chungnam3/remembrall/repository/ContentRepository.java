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

    @Query(value = "SELECT id FROM content WHERE id = :contentId FOR UPDATE", nativeQuery = true)
    Optional<UUID> findIdForUpdate(@Param("contentId") UUID contentId);

    /**
     * PENDING이면 최초 시도, FAILED + EXTRACTION_API_ERROR면 재시도로 다시 선점한다 - Gemini 호출
     * 자체의 일시적 실패(재시도하면 되는 경우, 실측 10건 중 2건)와 영상 자체가 없어진 경우
     * (VIDEO_UNAVAILABLE 등, 재시도해도 소용없음)를 구분한다(리뷰 반영, PR #20 "분석 중단 후 재시도"
     * 설계에서 보류됐던 항목 중 일부).
     */
    @Modifying
    @Query(value = """
            UPDATE content
               SET analysis_status = 'ANALYZING',
                   analysis_started_at = :startedAt,
                   updated_at = :startedAt
             WHERE id = :contentId
               AND (analysis_status = 'PENDING'
                   OR (analysis_status = 'FAILED' AND last_analysis_error_code = 'EXTRACTION_API_ERROR'))
            """, nativeQuery = true)
    int claimAnalysis(@Param("contentId") UUID contentId, @Param("startedAt") Instant startedAt);

    @Modifying
    @Query(value = """
            UPDATE content
               SET analysis_started_at = :startedAt, updated_at = :startedAt
             WHERE id = :contentId AND analysis_status = 'ANALYZING'
            """, nativeQuery = true)
    int refreshAnalysisStartedAt(@Param("contentId") UUID contentId, @Param("startedAt") Instant startedAt);

    @Modifying
    @Query(value = """
            UPDATE content
               SET analysis_status = 'PENDING', analysis_started_at = NULL, updated_at = :now
             WHERE id = :contentId AND analysis_status = 'ANALYZING'
            """, nativeQuery = true)
    int releaseAnalysisClaim(@Param("contentId") UUID contentId, @Param("now") Instant now);

    @Modifying
    @Query(value = """
            UPDATE content
               SET analysis_status = :status,
                   source_status = COALESCE(:sourceStatus, source_status),
                   title = COALESCE(:title, title),
                   summary = COALESCE(:summary, summary),
                   category = COALESCE(:category, category),
                   analysis_version = COALESCE(:analysisVersion, analysis_version),
                   last_analysis_error_code = :errorCode,
                   metadata_fetched_at = COALESCE(:metadataFetchedAt, metadata_fetched_at),
                   embedding = CAST(:embedding AS vector),
                   embedding_model = :embeddingModel,
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
            @Param("embedding") String embedding,
            @Param("embeddingModel") String embeddingModel,
            @Param("analyzedAt") Instant analyzedAt,
            @Param("updatedAt") Instant updatedAt
    );
}
