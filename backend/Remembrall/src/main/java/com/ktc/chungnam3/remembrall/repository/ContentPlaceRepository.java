package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.contentplace.ContentPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentPlaceRepository extends JpaRepository<ContentPlace, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO content_place (
                id,
                content_id,
                place_id,
                description,
                created_at,
                updated_at
            )
            VALUES (:id, :contentId, :placeId, :description, :now, :now)
            ON CONFLICT (content_id, place_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("contentId") UUID contentId,
            @Param("placeId") UUID placeId,
            @Param("description") String description,
            @Param("now") Instant now
    );

    Optional<ContentPlace> findByContent_IdAndPlace_Id(UUID contentId, UUID placeId);

    List<ContentPlace> findAllByContent_Id(UUID contentId);

    List<ContentPlace> findAllByPlace_Id(UUID placeId);

    @Query("SELECT cp.place.id FROM ContentPlace cp WHERE cp.content.id = :contentId")
    List<UUID> findPlaceIdsByContentId(@Param("contentId") UUID contentId);
}
