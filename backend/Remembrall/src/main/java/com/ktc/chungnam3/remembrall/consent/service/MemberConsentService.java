package com.ktc.chungnam3.remembrall.consent.service;

import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.consent.config.ConsentProperties;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentRequest;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentResponse;
import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import com.ktc.chungnam3.remembrall.domain.memberconsent.MemberConsent;
import com.ktc.chungnam3.remembrall.repository.MemberConsentRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class MemberConsentService {
    private final MemberConsentRepository consents;
    private final MemberRepository members;
    private final ConsentProperties properties;
    private final Clock clock;

    public MemberConsentService(MemberConsentRepository consents, MemberRepository members,
                                ConsentProperties properties, Clock clock) {
        this.consents = consents;
        this.members = members;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ConsentResponse> list(UUID memberId) {
        return Arrays.stream(ConsentType.values()).map(type -> current(memberId, type)).toList();
    }

    @Transactional
    public ConsentResponse change(UUID memberId, ConsentType type, ConsentRequest request) {
        members.findByIdForUpdate(memberId).orElseThrow(() -> new ApiException(ErrorCode.INVALID_SESSION));
        if (request.agreed() == null || request.agreed()
                && !properties.currentVersion(type).equals(request.termsVersion())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        var active = consents.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(memberId, type).orElse(null);
        if (request.agreed() && active != null && active.getTermsVersion().equals(request.termsVersion())) {
            return ConsentResponse.from(type, active);
        }
        if (!request.agreed() && active == null) {
            return current(memberId, type);
        }
        Instant now = clock.instant();
        if (active != null) {
            active.withdraw(now);
            consents.flush();
        }
        if (!request.agreed()) {
            return ConsentResponse.from(type, active);
        }
        var consent = consents.saveAndFlush(MemberConsent.create(memberId, type, request.termsVersion(), now));
        return ConsentResponse.from(type, consent);
    }

    private ConsentResponse current(UUID memberId, ConsentType type) {
        var consent = consents.findByMemberIdAndConsentTypeAndWithdrawnAtIsNull(memberId, type)
                .or(() -> consents.findFirstByMemberIdAndConsentTypeOrderByAgreedAtDescCreatedAtDescIdDesc(memberId, type))
                .orElse(null);
        return ConsentResponse.from(type, consent);
    }
}
