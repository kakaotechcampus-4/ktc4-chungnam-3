package com.ktc.chungnam3.remembrall.recall.service;

import com.ktc.chungnam3.remembrall.common.geo.GeoDistance;
import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import org.springframework.stereotype.Component;

@Component
public class WalkingTimeCalculator {
    private final RecallToolProperties properties;

    public WalkingTimeCalculator(RecallToolProperties properties) {
        this.properties = properties;
    }

    public double straightLineMeters(double fromLatitude, double fromLongitude,
                                     double toLatitude, double toLongitude) {
        return GeoDistance.meters(fromLatitude, fromLongitude, toLatitude, toLongitude);
    }

    public double estimatedMinutes(double straightLineMeters) {
        return straightLineMeters * properties.walkingDetourFactor() / properties.walkingSpeedMetersPerMinute();
    }
}
