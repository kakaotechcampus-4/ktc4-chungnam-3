package com.ktc.chungnam3.remembrall.recall.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Objects;

@Validated
@ConfigurationProperties(prefix = "recall")
public record RecallProperties(
        @DefaultValue("30m") Duration maxEventAge,
        @DefaultValue("2h") Duration memberCooldown,
        @DefaultValue("30") @Positive double maxCourseWalkingMinutes
) {
    public RecallProperties {
        Objects.requireNonNull(maxEventAge, "maxEventAge");
        Objects.requireNonNull(memberCooldown, "memberCooldown");
        if (maxEventAge.isNegative() || maxEventAge.isZero()) {
            throw new IllegalArgumentException("maxEventAge must be positive");
        }
        if (memberCooldown.isNegative()) {
            throw new IllegalArgumentException("memberCooldown must not be negative");
        }
        if (!Double.isFinite(maxCourseWalkingMinutes) || maxCourseWalkingMinutes <= 0) {
            throw new IllegalArgumentException("maxCourseWalkingMinutes must be finite and positive");
        }
    }
}
