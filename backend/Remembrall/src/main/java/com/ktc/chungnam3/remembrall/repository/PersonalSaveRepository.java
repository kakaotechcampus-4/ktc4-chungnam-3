package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.personalsave.PersonalSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PersonalSaveRepository extends JpaRepository<PersonalSave, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO personal_save (id, member_id, content_id, saved_at)
            VALUES (:id, :memberId, :contentId, :savedAt)
            ON CONFLICT (member_id, content_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("memberId") UUID memberId,
            @Param("contentId") UUID contentId,
            @Param("savedAt") Instant savedAt
    );

    Optional<PersonalSave> findByMemberIdAndContentId(UUID memberId, UUID contentId);

    @Query("SELECT ps.memberId FROM PersonalSave ps WHERE ps.contentId = :contentId")
    List<UUID> findMemberIdsByContentId(@Param("contentId") UUID contentId);

    @Query("""
            SELECT ps.contentId FROM PersonalSave ps
             WHERE ps.id = :personalSaveId AND ps.memberId = :memberId
            """)
    Optional<UUID> findOwnedContentId(
            @Param("personalSaveId") UUID personalSaveId,
            @Param("memberId") UUID memberId
    );

    @Modifying
    @Query(value = "DELETE FROM personal_save WHERE id = :personalSaveId AND member_id = :memberId",
            nativeQuery = true)
    int deleteOwnedSave(@Param("personalSaveId") UUID personalSaveId, @Param("memberId") UUID memberId);
}
