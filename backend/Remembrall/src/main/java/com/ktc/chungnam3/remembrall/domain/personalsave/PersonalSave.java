package com.ktc.chungnam3.remembrall.domain.personalsave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "personal_save", uniqueConstraints = @UniqueConstraint(
        name = "uk_personal_save_member_content", columnNames = {"member_id", "content_id"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonalSave {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;
}
