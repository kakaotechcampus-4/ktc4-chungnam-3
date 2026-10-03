package com.ktc.chungnam3.remembrall.recall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record LocationEventRequest(
        @NotNull UUID placeId,
        @NotNull UUID triggerEventId,
        @NotNull LocationEventType eventType,
        @NotNull Instant eventOccurredAt,
        @Valid Location location
) {
    public record Location(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @DecimalMin("0") Double accuracyMeters,
            @NotNull Instant locatedAt
    ) {
    }
}
