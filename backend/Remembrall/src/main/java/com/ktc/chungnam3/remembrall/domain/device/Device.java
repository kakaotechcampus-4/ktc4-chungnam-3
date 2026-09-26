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
                @UniqueConstraint(name = "uk_device_refresh_token_hash", columnNames = "refresh_token_hash")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device {

    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false)
    private UUID memberId;

    @Column(name = "refresh_token_hash", nullable = false, length = 64)
    private String refreshTokenHash;

    @Column(name = "refresh_token_expires_at", nullable = false)
    private Instant refreshTokenExpiresAt;

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Device(
            UUID id,
            UUID memberId,
            String refreshTokenHash,
            Instant refreshTokenExpiresAt,
            Instant createdAt
    ) {
        this.id = id;
        this.memberId = memberId;
        this.refreshTokenHash = refreshTokenHash;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Device create(
            UUID memberId,
            String refreshTokenHash,
            Instant refreshTokenExpiresAt,
            Instant createdAt
    ) {
        return new Device(
                UUID.randomUUID(),
                memberId,
                refreshTokenHash,
                refreshTokenExpiresAt,
                createdAt
        );
    }

    public void rotateRefreshToken(String refreshTokenHash, Instant refreshTokenExpiresAt, Instant updatedAt) {
        this.refreshTokenHash = refreshTokenHash;
        this.refreshTokenExpiresAt = refreshTokenExpiresAt;
        this.updatedAt = updatedAt;
    }

    public void updateFcmToken(String fcmToken, Instant updatedAt) {
        this.fcmToken = fcmToken;
        this.updatedAt = updatedAt;
    }
}
