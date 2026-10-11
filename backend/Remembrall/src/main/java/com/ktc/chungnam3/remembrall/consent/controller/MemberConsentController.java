package com.ktc.chungnam3.remembrall.consent.controller;

import com.ktc.chungnam3.remembrall.auth.security.AuthenticatedMember;
import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentRequest;
import com.ktc.chungnam3.remembrall.consent.dto.ConsentResponse;
import com.ktc.chungnam3.remembrall.consent.service.MemberConsentService;
import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me/consents")
public class MemberConsentController {
    private final MemberConsentService consents;

    public MemberConsentController(MemberConsentService consents) {
        this.consents = consents;
    }

    @GetMapping
    public List<ConsentResponse> list(@AuthenticationPrincipal AuthenticatedMember member) {
        return consents.list(member.memberId());
    }

    @PutMapping("/{consentType}")
    public ConsentResponse change(@AuthenticationPrincipal AuthenticatedMember member,
                                   @PathVariable String consentType, @Valid @RequestBody ConsentRequest request) {
        ConsentType type;
        try {
            type = ConsentType.valueOf(consentType);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        return consents.change(member.memberId(), type, request);
    }
}
