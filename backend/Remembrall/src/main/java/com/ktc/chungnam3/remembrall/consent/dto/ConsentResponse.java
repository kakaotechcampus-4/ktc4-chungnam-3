package com.ktc.chungnam3.remembrall.consent.dto;

import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.domain.memberconsent.MemberConsent;

import java.time.Instant;

public record ConsentResponse(ConsentType consentType, boolean agreed, String termsVersion,
                              Instant agreedAt, Instant withdrawnAt) {
    public static ConsentResponse from(ConsentType type, MemberConsent consent) {
        return consent == null ? new ConsentResponse(type, false, null, null, null)
                : new ConsentResponse(type, consent.getWithdrawnAt() == null, consent.getTermsVersion(),
                        consent.getAgreedAt(), consent.getWithdrawnAt());
    }
}
