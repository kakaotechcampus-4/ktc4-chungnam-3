package com.ktc.chungnam3.remembrall.domain.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "member",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_auth_provider_user_id",
                columnNames = {"auth_provider", "provider_user_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider;

    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Member(UUID id, AuthProvider authProvider, String providerUserId, Instant createdAt) {
        this.id = id;
        this.authProvider = authProvider;
        this.providerUserId = providerUserId;
        this.createdAt = createdAt;
    }

    public static Member create(AuthProvider authProvider, String providerUserId, Instant createdAt) {
        return new Member(UUID.randomUUID(), authProvider, providerUserId, createdAt);
    }
}
