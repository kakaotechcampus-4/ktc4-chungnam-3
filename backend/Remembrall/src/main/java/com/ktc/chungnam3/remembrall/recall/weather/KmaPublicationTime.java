package com.ktc.chungnam3.remembrall.recall.weather;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public final class KmaPublicationTime {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private KmaPublicationTime() {}

    public static ZonedDateTime observation(Instant now) {
        var available = now.atZone(SEOUL).minusMinutes(10).truncatedTo(ChronoUnit.MINUTES);
        return available.withMinute(available.getMinute() / 10 * 10);
    }

    public static ZonedDateTime forecast(Instant now) {
        var available = now.atZone(SEOUL).minusMinutes(10);
        var base = available.truncatedTo(ChronoUnit.DAYS).plusHours(23);
        while (base.isAfter(available)) base = base.minusHours(3);
        return base;
    }
}
