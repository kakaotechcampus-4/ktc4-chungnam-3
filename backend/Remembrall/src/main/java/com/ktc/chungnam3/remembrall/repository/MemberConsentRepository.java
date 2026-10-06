package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.domain.memberconsent.MemberConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberConsentRepository extends JpaRepository<MemberConsent, UUID> {
    Optional<MemberConsent> findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(UUID memberId, ConsentType consentType);

    Optional<MemberConsent> findFirstByMemberIdAndConsentTypeOrderByAgreedAtDescCreatedAtDescIdDesc(
            UUID memberId, ConsentType consentType);

    boolean existsByMemberIdAndConsentTypeAndWithdrawnAtIsNull(UUID memberId, ConsentType consentType);
}
