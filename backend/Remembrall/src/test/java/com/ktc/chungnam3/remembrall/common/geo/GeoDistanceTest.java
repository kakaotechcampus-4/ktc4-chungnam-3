package com.ktc.chungnam3.remembrall.common.geo;

import com.ktc.chungnam3.remembrall.recall.config.RecallToolProperties;
import com.ktc.chungnam3.remembrall.recall.service.WalkingTimeCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class GeoDistanceTest {
    @Test
    void samePointHasZeroDistance() {
        assertThat(GeoDistance.meters(37, 127, 37, 127)).isZero();
    }

    @Test
    void matchesExistingHaversineAndWalkingCalculation() {
        var walking = new WalkingTimeCalculator(new RecallToolProperties(5000, 20, 20, 20, 2000, 10, 1.3, 80));
        double distance = GeoDistance.meters(37, 127, 37.001, 127);

        assertThat(distance).isCloseTo(111.194926644, offset(0.000001));
        assertThat(walking.straightLineMeters(37, 127, 37.001, 127)).isEqualTo(distance);
        assertThat(walking.estimatedMinutes(distance)).isEqualTo(distance * 1.3 / 80);
    }

    @Test
    void crossesDateLineUsingShortestDistanceAndHandlesAntipodes() {
        assertThat(GeoDistance.meters(0, 179.999, 0, -179.999)).isCloseTo(222.389853289, offset(0.000001));
        assertThat(GeoDistance.meters(0, 0, 0, 180)).isCloseTo(Math.PI * 6371000, offset(0.000001));
    }
}
