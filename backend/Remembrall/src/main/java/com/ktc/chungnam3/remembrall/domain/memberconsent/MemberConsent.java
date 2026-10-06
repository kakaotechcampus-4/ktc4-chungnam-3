package com.ktc.chungnam3.remembrall.domain.memberconsent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "member_consent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberConsent {
    @Id
    private UUID id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, updatable = false, length = 50)
    private ConsentType consentType;

    @Column(name = "terms_version", nullable = false, updatable = false, length = 50)
    private String termsVersion;

    @Column(name = "agreed_at", nullable = false, updatable = false)
    private Instant agreedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static MemberConsent create(UUID memberId, ConsentType consentType, String termsVersion, Instant now) {
        MemberConsent consent = new MemberConsent();
        consent.id = UUID.randomUUID();
        consent.memberId = memberId;
        consent.consentType = consentType;
        consent.termsVersion = termsVersion;
        consent.agreedAt = now;
        consent.createdAt = now;
        return consent;
    }

    public void withdraw(Instant now) {
        if (withdrawnAt == null) {
            withdrawnAt = now;
        }
    }
}
