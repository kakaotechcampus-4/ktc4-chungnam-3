package com.ktc.chungnam3.remembrall.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "place", uniqueConstraints = @UniqueConstraint(
        name = "uk_place_verification_provider_place_id",
        columnNames = {"verification_provider", "verification_place_id"}
))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Place {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "geocoding_provider", nullable = false, length = 20)
    private GeocodingProvider geocodingProvider;

    @Column(name = "geocoding_place_id", nullable = false, length = 100)
    private String geocodingPlaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_provider", nullable = false, length = 20)
    private VerificationProvider verificationProvider;

    @Column(name = "verification_place_id", nullable = false, length = 100)
    private String verificationPlaceId;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Place(
            UUID id,
            String name,
            String address,
            double latitude,
            double longitude,
            GeocodingProvider geocodingProvider,
            String geocodingPlaceId,
            VerificationProvider verificationProvider,
            String verificationPlaceId,
            Instant verifiedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.geocodingProvider = geocodingProvider;
        this.geocodingPlaceId = geocodingPlaceId;
        this.verificationProvider = verificationProvider;
        this.verificationPlaceId = verificationPlaceId;
        this.verifiedAt = verifiedAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static Place create(
            String name,
            String address,
            double latitude,
            double longitude,
            GeocodingProvider geocodingProvider,
            String geocodingPlaceId,
            VerificationProvider verificationProvider,
            String verificationPlaceId,
            Instant verifiedAt,
            Instant createdAt
    ) {
        return new Place(
                UUID.randomUUID(),
                name,
                address,
                latitude,
                longitude,
                geocodingProvider,
                geocodingPlaceId,
                verificationProvider,
                verificationPlaceId,
                verifiedAt,
                createdAt
        );
    }
}
