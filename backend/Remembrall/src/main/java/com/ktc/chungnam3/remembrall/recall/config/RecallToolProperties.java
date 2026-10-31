package com.ktc.chungnam3.remembrall.recall.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "recall.tools")
public record RecallToolProperties(
        @DefaultValue("5000") @Positive double maxRadiusMeters,
        @DefaultValue("5") @Positive int searchPlaceLimit,
        @DefaultValue("5") @Positive int searchContentLimit,
        @DefaultValue("3") @Positive int maxVisitPlaces,
        @DefaultValue("2000") @Positive int visitSummaryLength,
        @DefaultValue("7d") Duration recentProposalWindow,
        @DefaultValue("1.3") @DecimalMin("1.0") double walkingDetourFactor,
        @DefaultValue("80") @Positive double walkingSpeedMetersPerMinute
) {
    public RecallToolProperties {
        if (!Double.isFinite(maxRadiusMeters) || !Double.isFinite(walkingDetourFactor)
                || !Double.isFinite(walkingSpeedMetersPerMinute) || recentProposalWindow == null
                || recentProposalWindow.isZero() || recentProposalWindow.isNegative() || maxVisitPlaces > 3) {
            throw new IllegalArgumentException("Recall tool settings must be finite");
        }
    }
}
