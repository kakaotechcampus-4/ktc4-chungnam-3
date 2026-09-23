package com.ktc.chungnam3.remembrall.domain.device;

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
@Table(
        name = "device",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_member", columnNames = "member_id"),
                @UniqueConstraint(name = "uk_device_session_token_hash", columnNames = "session_token_hash")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "session_token_hash", length = 64)
    private String sessionTokenHash;

    @Column(name = "session_expires_at")
    private Instant sessionExpiresAt;

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Device(
            UUID id,
            UUID memberId,
            String sessionTokenHash,
            Instant sessionExpiresAt,
            Instant createdAt
    ) {
        this.id = id;
        this.memberId = memberId;
        this.sessionTokenHash = sessionTokenHash;
        this.sessionExpiresAt = sessionExpiresAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Device create(
            UUID memberId,
            String sessionTokenHash,
            Instant sessionExpiresAt,
            Instant createdAt
    ) {
        return new Device(
                UUID.randomUUID(),
                memberId,
                sessionTokenHash,
                sessionExpiresAt,
                createdAt
        );
    }

    public void replaceSession(String sessionTokenHash, Instant sessionExpiresAt, Instant updatedAt) {
        this.sessionTokenHash = sessionTokenHash;
        this.sessionExpiresAt = sessionExpiresAt;
        this.fcmToken = null;
        this.updatedAt = updatedAt;
    }

    public void updateFcmToken(String fcmToken, Instant updatedAt) {
        this.fcmToken = fcmToken;
        this.updatedAt = updatedAt;
    }
}
