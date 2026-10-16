package com.ktc.chungnam3.remembrall.content;

import com.ktc.chungnam3.remembrall.common.exception.ApiException;
import com.ktc.chungnam3.remembrall.common.exception.ErrorCode;
import com.ktc.chungnam3.remembrall.content.dto.AnalysisOutcome;
import com.ktc.chungnam3.remembrall.content.dto.ContentSaveClaim;
import com.ktc.chungnam3.remembrall.content.service.ContentPersistenceService;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisFailureCode;
import com.ktc.chungnam3.remembrall.domain.content.ContentAnalysisStatus;
import com.ktc.chungnam3.remembrall.domain.content.ContentSourceStatus;
import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.place.VerificationProvider;
import com.ktc.chungnam3.remembrall.domain.trigger.Trigger;
import com.ktc.chungnam3.remembrall.repository.ContentPlaceRepository;
import com.ktc.chungnam3.remembrall.repository.ContentRepository;
import com.ktc.chungnam3.remembrall.repository.MemberRepository;
import com.ktc.chungnam3.remembrall.repository.PersonalSaveRepository;
import com.ktc.chungnam3.remembrall.repository.PlaceRepository;
import com.ktc.chungnam3.remembrall.repository.TriggerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TriggerPersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

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
    private TriggerRepository triggerRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ContentPersistenceService service;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        triggerRepository.deleteAll();
        contentPlaceRepository.deleteAll();
        placeRepository.deleteAll();
        personalSaveRepository.deleteAll();
        contentRepository.deleteAll();
        memberRepository.deleteAll();
        transactions = new TransactionTemplate(transactionManager);
        service = new ContentPersistenceService(
                contentRepository, personalSaveRepository, placeRepository, contentPlaceRepository,
                memberRepository, triggerRepository, Clock.fixed(NOW, ZoneOffset.UTC), transactionManager
        );
    }

    @Test
    void hasOnlyDesignedColumnsAndDatabaseRejectsDuplicateMemberPlace() {
        UUID memberId = createMember();
        analyze(memberId, "unique-trigger", "shared");
        Trigger trigger = triggerRepository.findAll().getFirst();

        assertThat(jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = 'trigger'
                 ORDER BY ordinal_position
                """, String.class)).containsExactly("id", "member_id", "place_id", "created_at");
        assertThat(trigger.getCreatedAt()).isEqualTo(NOW);
        Integer inserted = transactions.execute(status -> triggerRepository.insertIfAbsent(
                UUID.randomUUID(), memberId, trigger.getPlaceId(), NOW.plusSeconds(1)
        ));
        assertThat(inserted).isZero();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO trigger (id, member_id, place_id, created_at) VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), memberId, trigger.getPlaceId(), java.sql.Timestamp.from(NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_trigger_member_place");
        assertThat(triggerRepository.findAll()).extracting(Trigger::getId).containsExactly(trigger.getId());
    }

    @Test
    void rejectsMissingMemberAndPlaceForeignKeys() {
        UUID memberId = createMember();
        analyze(memberId, "trigger-fk", "shared");
        UUID placeId = findPlace("shared").getId();

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> triggerRepository.insertIfAbsent(
                UUID.randomUUID(), UUID.randomUUID(), placeId, NOW
        ))).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_trigger_member");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> triggerRepository.insertIfAbsent(
                UUID.randomUUID(), memberId, UUID.randomUUID(), NOW
        ))).isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_trigger_place");
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void createsTriggersForAllExistingSaversAndForSavesAfterAnalysis() {
        UUID firstMemberId = createMember();
        UUID secondMemberId = createMember();
        UUID lateMemberId = createMember();
        ContentSaveClaim claim = service.saveAndClaim(firstMemberId, "analysis-savers");
        service.saveAndClaim(secondMemberId, "analysis-savers");
        assertThat(triggerRepository.count()).isZero();

        assertThat(service.applyOutcome(claim.contentId(), outcome("one", "two"))).isTrue();
        assertThat(triggerRepository.count()).isEqualTo(4);
        service.saveAndClaim(lateMemberId, "analysis-savers");
        assertThat(triggerRepository.count()).isEqualTo(6);
        List<UUID> originalTriggerIds = triggerRepository.findAll().stream().map(Trigger::getId).toList();

        assertThat(service.saveAndClaim(lateMemberId, "analysis-savers").personalSaveCreated()).isFalse();
        analyze(firstMemberId, "another-shared-place", "one", "one");
        assertThat(triggerRepository.findAll()).extracting(Trigger::getId)
                .containsExactlyInAnyOrderElementsOf(originalTriggerIds);
        assertTriggersMatchSavedPlaces();
    }

    @ParameterizedTest
    @EnumSource(value = ContentAnalysisStatus.class, names = {"SUCCESS", "PARTIAL", "FAILED"})
    void doesNotCreateTriggersForEmptyPlaceList(ContentAnalysisStatus status) {
        UUID memberId = createMember();
        ContentSaveClaim claim = service.saveAndClaim(memberId, "empty-" + status);
        assertThat(service.applyOutcome(claim.contentId(), outcome(status, List.of()))).isTrue();
        service.saveAndClaim(createMember(), "empty-" + status);
        assertThat(triggerRepository.count()).isZero();
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void createsTriggersForVerifiedPlacesInPartialOutcome() {
        UUID memberId = createMember();
        ContentSaveClaim claim = service.saveAndClaim(memberId, "partial-trigger");
        assertThat(service.applyOutcome(claim.contentId(), outcome(
                ContentAnalysisStatus.PARTIAL, List.of(place("verified"))
        ))).isTrue();
        assertThat(triggerRepository.count()).isOne();
        service.saveAndClaim(createMember(), "partial-trigger");
        assertThat(triggerRepository.count()).isEqualTo(2);
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void failedConditionalUpdateDoesNotChangePlacesOrTriggers() {
        UUID memberId = createMember();
        ContentSaveClaim claim = analyze(memberId, "conditional-trigger", "original");
        UUID originalTriggerId = triggerRepository.findAll().getFirst().getId();

        assertThat(service.applyOutcome(claim.contentId(), outcome("late"))).isFalse();
        assertThat(service.applyOutcome(UUID.randomUUID(), outcome("missing-content"))).isFalse();
        assertThat(placeRepository.findAll()).extracting(Place::getVerificationPlaceId)
                .containsExactly("original");
        assertThat(contentPlaceRepository.count()).isOne();
        assertThat(triggerRepository.findAll()).extracting(Trigger::getId).containsExactly(originalTriggerId);
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void rollsBackOutcomeAndPlacesWhenTriggerInsertFails() {
        UUID memberId = createMember();
        ContentSaveClaim claim = service.saveAndClaim(memberId, "rollback-trigger");
        jdbcTemplate.execute("ALTER TABLE trigger ADD CONSTRAINT ck_test_trigger CHECK (member_id <> '"
                + memberId + "'::uuid)");
        try {
            assertThatThrownBy(() -> service.applyOutcome(claim.contentId(), outcome("rollback-place")))
                    .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_test_trigger");
            assertThat(contentRepository.findById(claim.contentId()).orElseThrow().getAnalysisStatus())
                    .isEqualTo(ContentAnalysisStatus.ANALYZING);
            assertThat(placeRepository.count()).isZero();
            assertThat(contentPlaceRepository.count()).isZero();
            assertThat(triggerRepository.count()).isZero();
        } finally {
            jdbcTemplate.execute("ALTER TABLE trigger DROP CONSTRAINT ck_test_trigger");
        }
    }

    @Test
    void keepsSharedTriggerUntilLastSaveAndChecksOwnership() {
        UUID memberId = createMember();
        UUID otherMemberId = createMember();
        ContentSaveClaim first = analyze(memberId, "delete-first", "shared", "first-only");
        ContentSaveClaim second = analyze(memberId, "delete-second", "shared");
        service.saveAndClaim(otherMemberId, "delete-first");
        UUID sharedPlaceId = findPlace("shared").getId();
        UUID sharedTriggerId = triggerRepository.findAll().stream()
                .filter(trigger -> trigger.getMemberId().equals(memberId)
                        && trigger.getPlaceId().equals(sharedPlaceId))
                .findFirst().orElseThrow().getId();

        assertThat(service.deletePersonalSave(otherMemberId, first.personalSaveId())).isFalse();
        assertThat(service.deletePersonalSave(memberId, UUID.randomUUID())).isFalse();
        assertThat(personalSaveRepository.findById(first.personalSaveId())).isPresent();
        assertThat(service.deletePersonalSave(memberId, first.personalSaveId())).isTrue();
        assertThat(triggerRepository.findById(sharedTriggerId)).isPresent();
        assertThat(triggerRepository.findAll()).filteredOn(trigger -> trigger.getMemberId().equals(memberId))
                .extracting(Trigger::getPlaceId).containsExactly(sharedPlaceId);
        assertTriggersMatchSavedPlaces();

        assertThat(service.deletePersonalSave(memberId, second.personalSaveId())).isTrue();
        assertThat(service.deletePersonalSave(memberId, second.personalSaveId())).isFalse();
        assertThat(triggerRepository.findAll()).extracting(Trigger::getMemberId).containsOnly(otherMemberId);
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void cascadesWhenMemberOrPlaceIsDeleted() {
        UUID firstMemberId = createMember();
        UUID secondMemberId = createMember();
        analyze(firstMemberId, "cascade-trigger", "one", "two");
        service.saveAndClaim(secondMemberId, "cascade-trigger");
        assertThat(triggerRepository.count()).isEqualTo(4);

        memberRepository.deleteById(firstMemberId);
        assertThat(triggerRepository.findAll()).extracting(Trigger::getMemberId).containsOnly(secondMemberId);
        assertThat(triggerRepository.count()).isEqualTo(2);
        placeRepository.deleteById(findPlace("one").getId());
        assertThat(triggerRepository.count()).isOne();
        assertThat(personalSaveRepository.count()).isOne();
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void unknownAuthenticatedMemberDoesNotPersistContentOrSave() {
        assertThatThrownBy(() -> service.saveAndClaim(UUID.randomUUID(), "invalid-member"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_SESSION));
        assertThat(contentRepository.count()).isZero();
        assertThat(personalSaveRepository.count()).isZero();
        assertThat(triggerRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void saveAndAnalysisCreateEveryTriggerInEitherTransactionOrder(boolean saveFirst) throws Exception {
        UUID firstMemberId = createMember();
        UUID secondMemberId = createMember();
        ContentSaveClaim claim = service.saveAndClaim(firstMemberId, "concurrent-save-analysis");
        Runnable save = () -> service.saveAndClaim(secondMemberId, "concurrent-save-analysis");
        Runnable analyze = () -> assertThat(service.applyOutcome(claim.contentId(), outcome("shared"))).isTrue();

        runWhileLocked(saveFirst ? save : analyze, List.of(saveFirst ? analyze : save), () -> {
            if (saveFirst) {
                assertThat(triggerRepository.count()).isZero();
            } else {
                assertThat(personalSaveRepository.findByMemberIdAndContentId(secondMemberId, claim.contentId()))
                        .isEmpty();
            }
        });

        assertThat(triggerRepository.count()).isEqualTo(2);
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void simultaneousSaveAndAnalysisWaitForSameContentRow() throws Exception {
        UUID firstMemberId = createMember();
        UUID secondMemberId = createMember();
        ContentSaveClaim claim = service.saveAndClaim(firstMemberId, "simultaneous-save-analysis");

        runWhileLocked(() -> contentRepository.findIdForUpdate(claim.contentId()).orElseThrow(), List.of(
                () -> service.saveAndClaim(secondMemberId, "simultaneous-save-analysis"),
                () -> assertThat(service.applyOutcome(claim.contentId(), outcome("shared"))).isTrue()
        ), () -> {
            assertThat(personalSaveRepository.findByMemberIdAndContentId(secondMemberId, claim.contentId()))
                    .isEmpty();
            assertThat(contentPlaceRepository.count()).isZero();
        });

        assertThat(triggerRepository.count()).isEqualTo(2);
        assertTriggersMatchSavedPlaces();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deletionAndAnalysisLeaveNoTriggerForDeletedSaveInEitherOrder(boolean deleteFirst) throws Exception {
        UUID memberId = createMember();
        ContentSaveClaim claim = service.saveAndClaim(memberId, "delete-during-analysis");
        Runnable delete = () -> assertThat(service.deletePersonalSave(memberId, claim.personalSaveId())).isTrue();
        Runnable analyze = () -> assertThat(service.applyOutcome(claim.contentId(), outcome("shared"))).isTrue();

        runWhileLocked(deleteFirst ? delete : analyze, List.of(deleteFirst ? analyze : delete), () -> {});

        assertThat(personalSaveRepository.count()).isZero();
        assertThat(triggerRepository.count()).isZero();
        assertThat(contentPlaceRepository.count()).isOne();
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void differentContentsSerializeSaveDeleteAndAnalysisBeforeChangingRelations() throws Exception {
        UUID memberId = createMember();
        UUID otherMemberId = createMember();
        ContentSaveClaim deleted = analyze(memberId, "overlap-deleted", "shared");
        ContentSaveClaim saved = analyze(otherMemberId, "overlap-saved", "shared");
        ContentSaveClaim analyzed = service.saveAndClaim(memberId, "overlap-analyzed");

        runWhileLocked(() -> memberRepository.findByIdForUpdate(memberId).orElseThrow(), List.of(
                () -> assertThat(service.deletePersonalSave(memberId, deleted.personalSaveId())).isTrue(),
                () -> service.saveAndClaim(memberId, "overlap-saved"),
                () -> assertThat(service.applyOutcome(analyzed.contentId(), outcome("shared", "extra"))).isTrue()
        ), () -> {
            assertThat(personalSaveRepository.findById(deleted.personalSaveId())).isPresent();
            assertThat(personalSaveRepository.findByMemberIdAndContentId(memberId, saved.contentId())).isEmpty();
            assertThat(contentPlaceRepository.findPlaceIdsByContentId(analyzed.contentId())).isEmpty();
        });

        assertThat(personalSaveRepository.findById(deleted.personalSaveId())).isEmpty();
        assertThat(personalSaveRepository.findByMemberIdAndContentId(memberId, saved.contentId())).isPresent();
        assertThat(triggerRepository.count()).isEqualTo(3);
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void concurrentDeletionOfDifferentContentsRemovesLastSharedTrigger() throws Exception {
        UUID memberId = createMember();
        ContentSaveClaim first = analyze(memberId, "concurrent-delete-first", "shared");
        ContentSaveClaim second = analyze(memberId, "concurrent-delete-second", "shared");

        runWhileLocked(() -> memberRepository.findByIdForUpdate(memberId).orElseThrow(), List.of(
                () -> assertThat(service.deletePersonalSave(memberId, first.personalSaveId())).isTrue(),
                () -> assertThat(service.deletePersonalSave(memberId, second.personalSaveId())).isTrue()
        ), () -> assertThat(personalSaveRepository.count()).isEqualTo(2));

        assertThat(personalSaveRepository.count()).isZero();
        assertThat(triggerRepository.count()).isZero();
        assertTriggersMatchSavedPlaces();
    }

    @Test
    void concurrentAnalysesLockAllSaversInTheSameMemberOrder() throws Exception {
        List<UUID> memberIds = List.of(createMember(), createMember()).stream().sorted().toList();
        UUID firstMemberId = memberIds.getFirst();
        UUID secondMemberId = memberIds.getLast();
        ContentSaveClaim first = service.saveAndClaim(secondMemberId, "ordered-analysis-first");
        service.saveAndClaim(firstMemberId, "ordered-analysis-first");
        ContentSaveClaim second = service.saveAndClaim(firstMemberId, "ordered-analysis-second");
        service.saveAndClaim(secondMemberId, "ordered-analysis-second");

        runWhileLocked(() -> memberRepository.findByIdForUpdate(firstMemberId).orElseThrow(), List.of(
                () -> assertThat(service.applyOutcome(first.contentId(), outcome("one", "two"))).isTrue(),
                () -> assertThat(service.applyOutcome(second.contentId(), outcome("two", "one"))).isTrue()
        ), () -> assertThat(contentPlaceRepository.count()).isZero());

        assertThat(triggerRepository.count()).isEqualTo(4);
        assertTriggersMatchSavedPlaces();
    }

    private UUID createMember() {
        return memberRepository.save(Member.create(AuthProvider.KAKAO, UUID.randomUUID().toString(), NOW)).getId();
    }

    private ContentSaveClaim analyze(UUID memberId, String videoId, String... placeIds) {
        ContentSaveClaim claim = service.saveAndClaim(memberId, videoId);
        assertThat(service.applyOutcome(claim.contentId(), outcome(placeIds))).isTrue();
        return claim;
    }

    private AnalysisOutcome outcome(String... placeIds) {
        return outcome(ContentAnalysisStatus.SUCCESS, List.of(placeIds).stream().map(this::place).toList());
    }

    private AnalysisOutcome outcome(ContentAnalysisStatus status, List<AnalysisOutcome.PlaceResult> places) {
        return new AnalysisOutcome(
                status, status == ContentAnalysisStatus.SUCCESS ? null : ContentAnalysisFailureCode.PLACE_SEARCH_API_ERROR,
                ContentSourceStatus.AVAILABLE, "Title", "Summary", null, "v1", NOW, places
        );
    }

    private AnalysisOutcome.PlaceResult place(String verificationId) {
        return new AnalysisOutcome.PlaceResult(
                "Description", "Place " + verificationId, "Address", 36.35, 127.38,
                com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider.LOCATIONIQ,
                "locationiq-" + verificationId, verificationId
        );
    }

    private Place findPlace(String verificationId) {
        return placeRepository.findByVerificationProviderAndVerificationPlaceId(
                VerificationProvider.KAKAO, verificationId).orElseThrow();
    }

    private void assertTriggersMatchSavedPlaces() {
        var expected = jdbcTemplate.query("""
                SELECT DISTINCT ps.member_id, cp.place_id
                  FROM personal_save ps JOIN content_place cp ON cp.content_id = ps.content_id
                """, (row, index) -> tuple(row.getObject(1, UUID.class), row.getObject(2, UUID.class)));
        assertThat(triggerRepository.findAll()).extracting(Trigger::getMemberId, Trigger::getPlaceId)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    private void runWhileLocked(Runnable lock, List<Runnable> actions, Runnable whileBlocked) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        List<Future<?>> futures = new ArrayList<>();
        try {
            transactions.executeWithoutResult(status -> {
                lock.run();
                Integer holderPid = jdbcTemplate.queryForObject("SELECT pg_backend_pid()", Integer.class);
                actions.forEach(action -> futures.add(executor.submit(action)));
                awaitBlockedTransactions(holderPid, actions.size());
                whileBlocked.run();
            });
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitBlockedTransactions(int holderPid, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int blocked = 0;
        while (System.nanoTime() < deadline) {
            blocked = jdbcTemplate.queryForObject("""
                    WITH RECURSIVE blocked(pid) AS (
                        SELECT pid FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                        UNION
                        SELECT activity.pid FROM pg_stat_activity activity
                        JOIN blocked ON blocked.pid = ANY(pg_blocking_pids(activity.pid))
                    )
                    SELECT COUNT(*) FROM blocked
                    """, Integer.class, holderPid);
            if (blocked == expected) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for database locks", exception);
            }
        }
        assertThat(blocked).as("transactions blocked on PostgreSQL backend %s", holderPid).isEqualTo(expected);
    }
}
