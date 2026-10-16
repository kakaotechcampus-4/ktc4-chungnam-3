package com.ktc.chungnam3.remembrall.content.dto;

import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisFailureCode;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AnalysisOutcome(
        ContentAnalysisStatus status,
        ContentAnalysisFailureCode failureCode,
        ContentSourceStatus sourceStatus,
        String title,
        String summary,
        String category,
        String analysisVersion,
        Instant metadataFetchedAt,
        List<PlaceResult> places
) {
    public AnalysisOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(places, "places");
        places = List.copyOf(places);
        if (!status.isTerminal()) {
            throw new IllegalArgumentException("Analysis outcome must have a terminal status");
        }
        if (status == ContentAnalysisStatus.SUCCESS && failureCode != null) {
            throw new IllegalArgumentException("Successful analysis cannot have a failure code");
        }
        if (status != ContentAnalysisStatus.SUCCESS && failureCode == null) {
            throw new IllegalArgumentException("Partial or failed analysis requires a failure code");
        }
        if (failureCode == ContentAnalysisFailureCode.VIDEO_UNAVAILABLE
                && sourceStatus != ContentSourceStatus.UNAVAILABLE) {
            throw new IllegalArgumentException(
                    "VIDEO_UNAVAILABLE requires sourceStatus UNAVAILABLE"
            );
        }
        if (title != null && title.length() > 500) {
            throw new IllegalArgumentException("title exceeds 500 characters");
        }
        if (category != null && category.length() > 50) {
            throw new IllegalArgumentException("category exceeds 50 characters");
        }
        if (analysisVersion != null && analysisVersion.length() > 30) {
            throw new IllegalArgumentException("analysisVersion exceeds 30 characters");
        }
    }

    public record PlaceResult(
            String description,
            String name,
            String address,
            double latitude,
            double longitude,
            GeocodingProvider geocodingProvider,
            String geocodingPlaceId,
            String verificationPlaceId
    ) {
        public PlaceResult {
            if (name == null || name.isBlank() || name.length() > 255) {
                throw new IllegalArgumentException("name must contain 1 to 255 characters");
            }
            if (address != null && address.length() > 500) {
                throw new IllegalArgumentException("address exceeds 500 characters");
            }
            if (latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException("latitude must be between -90 and 90");
            }
            if (longitude < -180 || longitude > 180) {
                throw new IllegalArgumentException("longitude must be between -180 and 180");
            }
            Objects.requireNonNull(geocodingProvider, "geocodingProvider");
            if (geocodingPlaceId == null || geocodingPlaceId.isBlank()
                    || geocodingPlaceId.length() > 100) {
                throw new IllegalArgumentException("geocodingPlaceId must contain 1 to 100 characters");
            }
            if (verificationPlaceId == null || verificationPlaceId.isBlank()
                    || verificationPlaceId.length() > 100) {
                throw new IllegalArgumentException("verificationPlaceId must contain 1 to 100 characters");
            }
        }
    }
}
