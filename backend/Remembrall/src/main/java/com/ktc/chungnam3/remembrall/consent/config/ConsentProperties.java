package com.ktc.chungnam3.remembrall.consent.config;

import com.ktc.chungnam3.remembrall.domain.memberconsent.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "consent")
public record ConsentProperties(
        @DefaultValue("v1") @NotBlank @Size(max = 50) String publicCandidateContributionTermsVersion,
        @DefaultValue("v1") @NotBlank @Size(max = 50) String locationBasedServiceTermsVersion
) {
    public String currentVersion(ConsentType type) {
        return switch (type) {
            case PUBLIC_CANDIDATE_CONTRIBUTION -> publicCandidateContributionTermsVersion;
            case LOCATION_BASED_SERVICE -> locationBasedServiceTermsVersion;
        };
    }
}
