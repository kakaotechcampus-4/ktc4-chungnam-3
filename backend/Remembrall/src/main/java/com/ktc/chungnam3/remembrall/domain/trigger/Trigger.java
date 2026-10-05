package com.ktc.chungnam3.remembrall.domain.trigger;

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
@Table(name = "trigger", uniqueConstraints = @UniqueConstraint(
        name = "uk_trigger_member_place", columnNames = {"member_id", "place_id"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trigger {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
