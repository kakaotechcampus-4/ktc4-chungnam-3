package com.ktc.chungnam3.remembrall.recall.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "recall.candidates")
public record RecallCandidateProperties(
        @DefaultValue("1000") @Positive double radiusMeters,
        @DefaultValue("10") @Min(0) int nearbyPersonalLimit,
        @DefaultValue("10") @Min(0) int nearbyPublicLimit,
        @DefaultValue("5") @Min(0) int semanticLimit,
        @DefaultValue("200") @Positive int maxSummaryLength
) {}
