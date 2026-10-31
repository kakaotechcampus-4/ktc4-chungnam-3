package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.trigger.Trigger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TriggerRepository extends JpaRepository<Trigger, UUID> {

    Optional<Trigger> findByMemberIdAndPlaceId(UUID memberId, UUID placeId);

    @Query(value = """
            SELECT p.id AS "placeId", p.latitude AS latitude, p.longitude AS longitude
              FROM trigger t
              JOIN place p ON p.id = t.place_id
             WHERE t.member_id = :memberId
               AND EXISTS (
                   SELECT 1
                     FROM content_place cp
                     JOIN content c ON c.id = cp.content_id
                     JOIN personal_save ps ON ps.content_id = c.id
                    WHERE cp.place_id = p.id
                      AND ps.member_id = :memberId
                      AND c.analysis_status <> 'FAILED'
                      AND c.source_status <> 'UNAVAILABLE'
               )
            """, nativeQuery = true)
    List<GeofencePlace> findEligibleGeofencePlaces(@Param("memberId") UUID memberId);

    interface GeofencePlace {
        UUID getPlaceId();
        double getLatitude();
        double getLongitude();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT trigger FROM Trigger trigger WHERE trigger.memberId = :memberId AND trigger.placeId = :placeId")
    Optional<Trigger> findByMemberIdAndPlaceIdForUpdate(
            @Param("memberId") UUID memberId,
            @Param("placeId") UUID placeId
    );

    @Modifying
    @Query(value = """
            INSERT INTO trigger (id, member_id, place_id, created_at)
            VALUES (:id, :memberId, :placeId, :createdAt)
            ON CONFLICT (member_id, place_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("memberId") UUID memberId,
            @Param("placeId") UUID placeId,
            @Param("createdAt") Instant createdAt
    );

    @Modifying
    @Query(value = """
            DELETE FROM trigger
             WHERE member_id = :memberId
               AND place_id = :placeId
               AND NOT EXISTS (
                   SELECT 1
                     FROM personal_save ps
                     JOIN content_place cp ON cp.content_id = ps.content_id
                    WHERE ps.member_id = :memberId
                      AND cp.place_id = :placeId
               )
            """, nativeQuery = true)
    int deleteIfUnreferenced(@Param("memberId") UUID memberId, @Param("placeId") UUID placeId);
}
