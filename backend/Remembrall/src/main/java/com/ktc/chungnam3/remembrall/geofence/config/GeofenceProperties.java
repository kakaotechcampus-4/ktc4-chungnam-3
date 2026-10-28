package com.ktc.chungnam3.remembrall.geofence.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "geofence")
public record GeofenceProperties(
        @DefaultValue("20") @Min(1) @Max(20) int iosLimit,
        @DefaultValue("100") @Min(1) @Max(100) int androidLimit,
        @DefaultValue("150") @Positive int placeRadiusMeters,
        @DefaultValue("500") @PositiveOrZero double resyncMarginMeters,
        @DefaultValue("3000") @Positive double resyncMinRadiusMeters
) {
    public GeofenceProperties {
        if (!Double.isFinite(resyncMarginMeters) || !Double.isFinite(resyncMinRadiusMeters)) {
            throw new IllegalArgumentException("Geofence radii must be finite");
        }
    }
}
