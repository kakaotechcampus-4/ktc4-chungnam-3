package com.ktc.chungnam3.remembrall.content;

import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.content.service.ContentAnalysisResultService;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.content.Content;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisFailureCode;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.contentplace.ContentPlace;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentAnalysisResultIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-28T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private PersonalSaveRepository personalSaveRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private ContentPlaceRepository contentPlaceRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ContentPersistenceService persistenceService;
    private ContentAnalysisResultService resultService;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        contentPlaceRepository.deleteAll();
        placeRepository.deleteAll();
        personalSaveRepository.deleteAll();
        contentRepository.deleteAll();
        memberRepository.deleteAll();
        persistenceService = new ContentPersistenceService(
                contentRepository,
                personalSaveRepository,
                placeRepository,
                contentPlaceRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager
        );
        resultService = new ContentAnalysisResultService(persistenceService);
        memberId = memberRepository.save(Member.create(
                AuthProvider.KAKAO,
                UUID.randomUUID().toString(),
                NOW
        )).getId();
    }

    @Test
    void storesSuccessWithoutPlacesAndKeepsUnknownSourceStatusUnchanged() {
        ContentSaveClaim claim = persistenceService.saveAndClaim(memberId, "success-without-place");
        jdbcTemplate.update(
                "UPDATE content SET source_status = 'AVAILABLE' WHERE id = ?",
                claim.contentId()
        );

        assertThat(resultService.applyOutcome(claim.contentId(), outcome(
                ContentAnalysisStatus.SUCCESS,
                null,
                null,
                List.of()
        ))).isTrue();

        Content content = findContent(claim);
        assertThat(content.getAnalysisStatus()).isEqualTo(ContentAnalysisStatus.SUCCESS);
        assertThat(content.getSourceStatus()).isEqualTo(ContentSourceStatus.AVAILABLE);
        assertThat(content.getLastAnalysisErrorCode()).isNull();
        assertThat(content.getAnalyzedAt()).isEqualTo(NOW);
        assertThat(placeRepository.count()).isZero();
        assertThat(contentPlaceRepository.count()).isZero();
    }

    @Test
    void storesPlacesReusesKakaoPlaceAndPreventsDuplicateContentPlace() {
        ContentSaveClaim firstClaim = persistenceService.saveAndClaim(memberId, "first-place-result");
        ContentSaveClaim secondClaim = persistenceService.saveAndClaim(memberId, "second-place-result");
        AnalysisOutcome.PlaceResult firstPlace = place(
                "First description",
                "LocationIQ stored name",
                "LocationIQ stored address",
                "locationiq-1",
                "kakao-shared"
        );
        AnalysisOutcome.PlaceResult duplicateInSameContent = place(
                "Ignored duplicate description",
                "Other name",
                "Other address",
                "locationiq-duplicate",
                "kakao-shared"
        );
        AnalysisOutcome.PlaceResult reusedByOtherContent = place(
                "Second description",
                "Changed LocationIQ name",
                "Changed address",
                "locationiq-2",
                "kakao-shared"
        );

        assertThat(resultService.applyOutcome(firstClaim.contentId(), outcome(
                ContentAnalysisStatus.SUCCESS,
                null,
                ContentSourceStatus.AVAILABLE,
                List.of(firstPlace, duplicateInSameContent)
        ))).isTrue();
        assertThat(resultService.applyOutcome(secondClaim.contentId(), outcome(
                ContentAnalysisStatus.SUCCESS,
                null,
                ContentSourceStatus.AVAILABLE,
                List.of(reusedByOtherContent)
        ))).isTrue();

        assertThat(placeRepository.count()).isOne();
        Place storedPlace = placeRepository.findAll().getFirst();
        assertThat(storedPlace.getName()).isEqualTo("LocationIQ stored name");
        assertThat(storedPlace.getGeocodingPlaceId()).isEqualTo("locationiq-1");
        assertThat(storedPlace.getVerificationPlaceId()).isEqualTo("kakao-shared");
        assertThat(contentPlaceRepository.count()).isEqualTo(2);
        assertThat(contentPlaceRepository.findByContent_IdAndPlace_Id(
                firstClaim.contentId(), storedPlace.getId())).get()
                .extracting(ContentPlace::getDescription)
                .isEqualTo("First description");
        assertThat(contentPlaceRepository.findByContent_IdAndPlace_Id(
                secondClaim.contentId(), storedPlace.getId())).get()
                .extracting(ContentPlace::getDescription)
                .isEqualTo("Second description");
    }

    @Test
    void storesPartialAndFailedWithStableFailureCodes() {
        ContentSaveClaim partialClaim = persistenceService.saveAndClaim(memberId, "partial-final-result");
        ContentSaveClaim failedClaim = persistenceService.saveAndClaim(memberId, "failed-final-result");

        assertThat(resultService.applyOutcome(partialClaim.contentId(), outcome(
                ContentAnalysisStatus.PARTIAL,
                ContentAnalysisFailureCode.PLACE_SEARCH_API_ERROR,
                ContentSourceStatus.AVAILABLE,
                List.of()
        ))).isTrue();
        assertThat(resultService.applyOutcome(failedClaim.contentId(), outcome(
                ContentAnalysisStatus.FAILED,
                ContentAnalysisFailureCode.VIDEO_UNAVAILABLE,
                ContentSourceStatus.UNAVAILABLE,
                List.of()
        ))).isTrue();

        Content partial = findContent(partialClaim);
        Content failed = findContent(failedClaim);
        assertThat(partial.getAnalysisStatus()).isEqualTo(ContentAnalysisStatus.PARTIAL);
        assertThat(partial.getLastAnalysisErrorCode()).isEqualTo("PLACE_SEARCH_API_ERROR");
        assertThat(partial.getAnalyzedAt()).isEqualTo(NOW);
        assertThat(failed.getAnalysisStatus()).isEqualTo(ContentAnalysisStatus.FAILED);
        assertThat(failed.getLastAnalysisErrorCode()).isEqualTo("VIDEO_UNAVAILABLE");
        assertThat(failed.getAnalyzedAt()).isEqualTo(NOW);
    }

    @Test
    void doesNotStorePlacesWhenConditionalOutcomeUpdateFails() {
        ContentSaveClaim claim = persistenceService.saveAndClaim(memberId, "late-place-result");
        assertThat(resultService.applyOutcome(claim.contentId(), outcome(
                ContentAnalysisStatus.SUCCESS,
                null,
                ContentSourceStatus.AVAILABLE,
                List.of()
        ))).isTrue();

        assertThat(resultService.applyOutcome(claim.contentId(), outcome(
                ContentAnalysisStatus.PARTIAL,
                ContentAnalysisFailureCode.PLACE_VERIFICATION_API_ERROR,
                ContentSourceStatus.AVAILABLE,
                List.of(place(
                        "Late description",
                        "Late place",
                        "Late address",
                        "late-locationiq",
                        "late-kakao"
                ))
        ))).isFalse();

        assertThat(placeRepository.count()).isZero();
        assertThat(contentPlaceRepository.count()).isZero();
        assertThat(findContent(claim).getAnalysisStatus()).isEqualTo(ContentAnalysisStatus.SUCCESS);
    }

    private AnalysisOutcome outcome(
            ContentAnalysisStatus status,
            ContentAnalysisFailureCode failureCode,
            ContentSourceStatus sourceStatus,
            List<AnalysisOutcome.PlaceResult> places
    ) {
        return new AnalysisOutcome(
                status,
                failureCode,
                sourceStatus,
                "Title",
                "Summary",
                null,
                "v1",
                Instant.parse("2026-09-27T00:00:00Z"),
                places
        );
    }

    private AnalysisOutcome.PlaceResult place(
            String description,
            String name,
            String address,
            String geocodingPlaceId,
            String verificationPlaceId
    ) {
        return new AnalysisOutcome.PlaceResult(
                description,
                name,
                address,
                36.35,
                127.38,
                geocodingPlaceId,
                verificationPlaceId
        );
    }

    private Content findContent(ContentSaveClaim claim) {
        return contentRepository.findById(claim.contentId()).orElseThrow();
    }
}
