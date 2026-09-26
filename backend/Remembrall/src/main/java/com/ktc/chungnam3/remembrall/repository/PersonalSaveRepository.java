package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.personalsave.PersonalSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
