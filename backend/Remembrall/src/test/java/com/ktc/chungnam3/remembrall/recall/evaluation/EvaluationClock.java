package com.ktc.chungnam3.remembrall.recall.evaluation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class EvaluationClock extends Clock {
    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public EvaluationClock() {
        this(new AtomicReference<>(Instant.now()), ZoneOffset.UTC);
    }

    private EvaluationClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = zone;
    }

    public void set(Instant instant) { now.set(instant); }
    @Override public ZoneId getZone() { return zone; }
    @Override public Clock withZone(ZoneId zone) { return new EvaluationClock(now, zone); }
    @Override public Instant instant() { return now.get(); }
}
