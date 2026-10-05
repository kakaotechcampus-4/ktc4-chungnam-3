package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.trigger.Trigger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TriggerRepository extends JpaRepository<Trigger, UUID> {

    Optional<Trigger> findByMemberIdAndPlaceId(UUID memberId, UUID placeId);

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
