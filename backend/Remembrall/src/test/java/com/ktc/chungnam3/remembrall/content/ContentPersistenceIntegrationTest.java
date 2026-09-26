package com.ktc.chungnam3.remembrall.content;

import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.content.Content;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentPersistenceIntegrationTest {

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
    private PlatformTransactionManager transactionManager;

    private ContentPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ContentPersistenceService(
                contentRepository,
                personalSaveRepository,
                Clock.systemUTC(),
                transactionManager
        );
    }

    @Test
    void concurrentSavesBySameMemberCreateOneSaveAndOneAnalysisClaim() throws Exception {
        UUID memberId = createMember();
        String videoId = "same-member-video";

        List<ContentSaveClaim> claims = saveConcurrently(List.of(memberId, memberId), videoId);

        assertThat(claims).extracting(ContentSaveClaim::contentId).containsOnly(claims.getFirst().contentId());
        assertThat(claims).extracting(ContentSaveClaim::personalSaveId).containsOnly(claims.getFirst().personalSaveId());
        assertThat(claims).filteredOn(ContentSaveClaim::personalSaveCreated).hasSize(1);
        assertThat(claims).filteredOn(ContentSaveClaim::analysisClaimed).hasSize(1);
        assertThat(claims).extracting(ContentSaveClaim::analysisStatus)
                .containsOnly(ContentAnalysisStatus.ANALYZING);
        assertThat(contentRepository.findAll().stream().filter(content -> videoId.equals(content.getVideoId())))
                .hasSize(1);
        assertThat(personalSaveRepository.findAll().stream()
                .filter(save -> claims.getFirst().contentId().equals(save.getContentId())))
                .hasSize(1);
    }

    @Test
    void concurrentSavesByDifferentMembersShareContentAndClaimAnalysisOnce() throws Exception {
        UUID firstMemberId = createMember();
        UUID secondMemberId = createMember();
        String videoId = "shared-member-video";

        List<ContentSaveClaim> claims = saveConcurrently(List.of(firstMemberId, secondMemberId), videoId);

        assertThat(claims).extracting(ContentSaveClaim::contentId).containsOnly(claims.getFirst().contentId());
        assertThat(claims).extracting(ContentSaveClaim::personalSaveId).doesNotHaveDuplicates();
        assertThat(claims).filteredOn(ContentSaveClaim::personalSaveCreated).hasSize(2);
        assertThat(claims).filteredOn(ContentSaveClaim::analysisClaimed).hasSize(1);
        assertThat(contentRepository.findAll().stream().filter(content -> videoId.equals(content.getVideoId())))
                .hasSize(1);
        assertThat(personalSaveRepository.findAll().stream()
                .filter(save -> claims.getFirst().contentId().equals(save.getContentId())))
                .hasSize(2);
    }

    @Test
    void completedOutcomeStoresDesignedFieldsAndCannotBeAppliedTwice() {
        UUID memberId = createMember();
        ContentSaveClaim claim = service.saveAndClaim(memberId, "completed-video");
        Instant fetchedAt = Instant.parse("2026-09-25T01:00:00Z");
        AnalysisOutcome outcome = new AnalysisOutcome(
                ContentAnalysisStatus.COMPLETED,
                ContentSourceStatus.AVAILABLE,
                "Video title",
                "Video summary",
                "TRAVEL",
                "v1",
                null,
                fetchedAt
        );

        assertThat(service.applyOutcome(claim.contentId(), outcome)).isTrue();
        Instant analyzedAt = contentRepository.findById(claim.contentId()).orElseThrow().getAnalyzedAt();
        assertThat(service.applyOutcome(claim.contentId(), new AnalysisOutcome(
                ContentAnalysisStatus.FAILED,
                ContentSourceStatus.UNAVAILABLE,
                null,
                null,
                null,
                null,
                "LATE_FAILURE",
                null
        ))).isFalse();

        Content content = contentRepository.findById(claim.contentId()).orElseThrow();
        assertThat(content.getAnalysisStatus()).isEqualTo(ContentAnalysisStatus.COMPLETED);
        assertThat(content.getSourceStatus()).isEqualTo(ContentSourceStatus.AVAILABLE);
        assertThat(content.getTitle()).isEqualTo("Video title");
        assertThat(content.getSummary()).isEqualTo("Video summary");
        assertThat(content.getCategory()).isEqualTo("TRAVEL");
        assertThat(content.getAnalysisVersion()).isEqualTo("v1");
        assertThat(content.getLastAnalysisErrorCode()).isNull();
        assertThat(content.getMetadataFetchedAt()).isEqualTo(fetchedAt);
        assertThat(content.getAnalysisStartedAt()).isNotNull();
        assertThat(analyzedAt).isNotNull();
        assertThat(content.getAnalyzedAt()).isEqualTo(analyzedAt);
        assertThat(content.getCreatedAt()).isNotNull();
        assertThat(content.getUpdatedAt()).isNotNull();
    }

    @Test
    void partialAndFailedOutcomesKeepPersonalSavesAndRecordAnalyzedAt() {
        UUID memberId = createMember();
        ContentSaveClaim partialClaim = service.saveAndClaim(memberId, "partial-video");
        ContentSaveClaim failedClaim = service.saveAndClaim(memberId, "failed-video");

        assertThat(service.applyOutcome(partialClaim.contentId(), new AnalysisOutcome(
                ContentAnalysisStatus.PARTIAL_SUCCESS,
                ContentSourceStatus.AVAILABLE,
                "Partial title",
                "Partial summary",
                null,
                "v1",
                "PLACE_SEARCH_ERROR",
                Instant.parse("2026-09-25T01:00:00Z")
        ))).isTrue();
        assertThat(service.applyOutcome(failedClaim.contentId(), new AnalysisOutcome(
                ContentAnalysisStatus.FAILED,
                ContentSourceStatus.UNAVAILABLE,
                null,
                null,
                null,
                null,
                "METADATA_FETCH_ERROR",
                null
        ))).isTrue();

        Instant partialAnalyzedAt = contentRepository.findById(partialClaim.contentId())
                .orElseThrow().getAnalyzedAt();
        Instant failedAnalyzedAt = contentRepository.findById(failedClaim.contentId())
                .orElseThrow().getAnalyzedAt();
        AnalysisOutcome lateCompletion = new AnalysisOutcome(
                ContentAnalysisStatus.COMPLETED,
                ContentSourceStatus.AVAILABLE,
                "Late title",
                "Late summary",
                null,
                "v2",
                null,
                null
        );
        assertThat(service.applyOutcome(partialClaim.contentId(), lateCompletion)).isFalse();
        assertThat(service.applyOutcome(failedClaim.contentId(), lateCompletion)).isFalse();

        Content partial = contentRepository.findById(partialClaim.contentId()).orElseThrow();
        Content failed = contentRepository.findById(failedClaim.contentId()).orElseThrow();
        assertThat(partial.getAnalysisStatus()).isEqualTo(ContentAnalysisStatus.PARTIAL_SUCCESS);
        assertThat(partial.getSummary()).isEqualTo("Partial summary");
        assertThat(partial.getCategory()).isNull();
        assertThat(partial.getLastAnalysisErrorCode()).isEqualTo("PLACE_SEARCH_ERROR");
        assertThat(partialAnalyzedAt).isNotNull();
        assertThat(partial.getAnalyzedAt()).isEqualTo(partialAnalyzedAt);
        assertThat(failed.getAnalysisStatus()).isEqualTo(ContentAnalysisStatus.FAILED);
        assertThat(failed.getLastAnalysisErrorCode()).isEqualTo("METADATA_FETCH_ERROR");
        assertThat(failedAnalyzedAt).isNotNull();
        assertThat(failed.getAnalyzedAt()).isEqualTo(failedAnalyzedAt);
        assertThat(personalSaveRepository.findById(partialClaim.personalSaveId())).isPresent();
        assertThat(personalSaveRepository.findById(failedClaim.personalSaveId())).isPresent();
    }

    private UUID createMember() {
        return memberRepository.save(Member.create(
                AuthProvider.KAKAO,
                UUID.randomUUID().toString(),
                Instant.now()
        )).getId();
    }

    private List<ContentSaveClaim> saveConcurrently(List<UUID> memberIds, String videoId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(memberIds.size());
        CountDownLatch ready = new CountDownLatch(memberIds.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ContentSaveClaim>> futures = memberIds.stream()
                    .map(memberId -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return service.saveAndClaim(memberId, videoId);
                    }))
                    .toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    futures.get(0).get(30, TimeUnit.SECONDS),
                    futures.get(1).get(30, TimeUnit.SECONDS)
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}
