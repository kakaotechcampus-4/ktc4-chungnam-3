package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.place.VerificationProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PlaceRepository extends JpaRepository<Place, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO place (
                id,
                name,
                address,
                latitude,
                longitude,
                geocoding_provider,
                geocoding_place_id,
                verification_provider,
                verification_place_id,
                verified_at,
                created_at,
                updated_at
            )
            VALUES (
                :id,
                :name,
                :address,
                :latitude,
                :longitude,
                'LOCATIONIQ',
                :geocodingPlaceId,
                'KAKAO',
                :verificationPlaceId,
                :now,
                :now,
                :now
            )
            ON CONFLICT (verification_provider, verification_place_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("address") String address,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("geocodingPlaceId") String geocodingPlaceId,
            @Param("verificationPlaceId") String verificationPlaceId,
            @Param("now") Instant now
    );

    Optional<Place> findByVerificationProviderAndVerificationPlaceId(
            VerificationProvider verificationProvider,
            String verificationPlaceId
    );
}
