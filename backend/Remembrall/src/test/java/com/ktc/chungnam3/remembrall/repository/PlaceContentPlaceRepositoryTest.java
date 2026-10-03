package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.content.Content;
import com.ktc.chungnam3.remembrall.domain.contentplace.ContentPlace;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.place.VerificationProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.persistence.EntityManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class PlaceContentPlaceRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ContentPlaceRepository contentPlaceRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void rejectsContentPlaceWhenContentForeignKeyDoesNotExist() {
        Place place = savePlace("kakao-fk-content", "locationiq-fk-content", 36.3, 127.3);

        assertThatThrownBy(() -> contentPlaceRepository.saveAndFlush(ContentPlace.create(
                entityManager.getReference(Content.class, UUID.randomUUID()),
                place,
                "content context",
                NOW
        ))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_content_place_content");
    }

    @Test
    void rejectsContentPlaceWhenPlaceForeignKeyDoesNotExist() {
        Content content = insertContent("missing-place-video");

        assertThatThrownBy(() -> contentPlaceRepository.saveAndFlush(ContentPlace.create(
                content,
                entityManager.getReference(Place.class, UUID.randomUUID()),
                "content context",
                NOW
        ))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_content_place_place");
    }

    @Test
    void rejectsDuplicateContentAndPlaceLink() {
        Content content = insertContent("duplicate-link-video");
        Place place = savePlace("kakao-duplicate-link", "locationiq-duplicate-link", 36.4, 127.4);
        contentPlaceRepository.saveAndFlush(ContentPlace.create(content, place, "first", NOW));

        assertThatThrownBy(() -> contentPlaceRepository.saveAndFlush(
                ContentPlace.create(content, place, "second", NOW)
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_content_place_content_place");
    }

    @Test
    void multipleContentsCanReferenceOnePlaceAndContentCanHaveNoPlace() {
        Content firstContent = insertContent("first-shared-place-video");
        Content secondContent = insertContent("second-shared-place-video");
        Content noPlaceContent = insertContent("no-place-video");
        Place place = savePlace("kakao-shared", "locationiq-shared", 36.5, 127.5);

        contentPlaceRepository.saveAndFlush(
                ContentPlace.create(firstContent, place, "first context", NOW));
        contentPlaceRepository.saveAndFlush(
                ContentPlace.create(secondContent, place, "second context", NOW));

        assertThat(contentPlaceRepository.findAllByPlace_Id(place.getId()))
                .extracting(contentPlace -> contentPlace.getContent().getId())
                .containsExactlyInAnyOrder(firstContent.getId(), secondContent.getId());
        assertThat(contentPlaceRepository.findAllByContent_Id(noPlaceContent.getId())).isEmpty();
        assertThat(placeRepository.count()).isOne();
    }

    @Test
    void allowsDuplicateGeocodingIdentityAndCoordinatesButRejectsDuplicateVerificationIdentity() {
        Place first = savePlace("kakao-unique", "same-locationiq-id", 36.6, 127.6);
        Place second = savePlace("kakao-other", "same-locationiq-id", 36.6, 127.6);

        assertThat(placeRepository.findAll()).extracting(Place::getId)
                .containsExactlyInAnyOrder(first.getId(), second.getId());
        assertThatThrownBy(() -> placeRepository.saveAndFlush(Place.create(
                "Duplicate Kakao place",
                "Other address",
                37.0,
                128.0,
                GeocodingProvider.LOCATIONIQ,
                "other-locationiq-id",
                VerificationProvider.KAKAO,
                "kakao-unique",
                NOW,
                NOW
        ))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_place_verification_provider_place_id");
    }

    @ParameterizedTest
    @CsvSource({
            "-90.0001, 0.0, ck_place_latitude",
            "90.0001, 0.0, ck_place_latitude",
            "0.0, -180.0001, ck_place_longitude",
            "0.0, 180.0001, ck_place_longitude"
    })
    void rejectsCoordinatesOutsideValidRange(
            double latitude,
            double longitude,
            String constraintName
    ) {
        assertThatThrownBy(() -> placeRepository.saveAndFlush(Place.create(
                "Invalid coordinate place",
                null,
                latitude,
                longitude,
                GeocodingProvider.LOCATIONIQ,
                UUID.randomUUID().toString(),
                VerificationProvider.KAKAO,
                UUID.randomUUID().toString(),
                NOW,
                NOW
        ))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining(constraintName);
    }

    private Content insertContent(String videoId) {
        UUID contentId = UUID.randomUUID();
        assertThat(contentRepository.insertIfAbsent(contentId, videoId, NOW)).isOne();
        return contentRepository.findById(contentId).orElseThrow();
    }

    private Place savePlace(
            String verificationPlaceId,
            String geocodingPlaceId,
            double latitude,
            double longitude
    ) {
        return placeRepository.saveAndFlush(Place.create(
                "Place " + verificationPlaceId,
                "123 Test road",
                latitude,
                longitude,
                GeocodingProvider.LOCATIONIQ,
                geocodingPlaceId,
                VerificationProvider.KAKAO,
                verificationPlaceId,
                NOW,
                NOW
        ));
    }
}
