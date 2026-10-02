package com.ktc.chungnam3.remembrall.repository;

import com.ktc.chungnam3.remembrall.domain.member.AuthProvider;
import com.ktc.chungnam3.remembrall.domain.member.Member;
import com.ktc.chungnam3.remembrall.domain.place.GeocodingProvider;
import com.ktc.chungnam3.remembrall.domain.place.Place;
import com.ktc.chungnam3.remembrall.domain.place.VerificationProvider;
import com.ktc.chungnam3.remembrall.domain.recallexecution.RecallExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
class RecallExecutionRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlaceRepository placeRepository;

    @Autowired
    private TriggerRepository triggerRepository;

    @Autowired
    private RecallExecutionRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID triggerId;

    @BeforeEach
    void setUp() {
        UUID memberId = memberRepository.saveAndFlush(Member.create(
                AuthProvider.KAKAO, UUID.randomUUID().toString(), NOW)).getId();
        UUID placeId = placeRepository.saveAndFlush(Place.create(
                "Place", "Address", 36.35, 127.38, GeocodingProvider.LOCATIONIQ, UUID.randomUUID().toString(),
                VerificationProvider.KAKAO, UUID.randomUUID().toString(), NOW, NOW)).getId();
        triggerId = UUID.randomUUID();
        triggerRepository.insertIfAbsent(triggerId, memberId, placeId, NOW);
    }

    @Test
    void containsExactlyDesignedFieldsAndNoAdditionalLookupIndexes() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = 'recall_execution' ORDER BY ordinal_position
                """, String.class)).containsExactly(
                "id", "trigger_id", "trigger_event_id", "status", "result", "proposal_type", "proposal_payload",
                "decision_summary", "failure_code", "agent_version", "event_occurred_at", "started_at",
                "finished_at", "created_at", "updated_at");
        assertThat(jdbcTemplate.queryForList("""
                SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'recall_execution'
                """, String.class)).containsExactlyInAnyOrder(
                "recall_execution_pkey", "uk_recall_execution_trigger_event");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                 WHERE conrelid = 'recall_execution'::regclass AND conname = 'uk_recall_execution_trigger_event'
                """, String.class)).isEqualTo("UNIQUE (trigger_id, trigger_event_id)");
    }

    @Test
    void databaseIgnoresConflictingEventIdAndKeepsPendingFields() {
        UUID id = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        assertThat(repository.insertPendingIfAbsent(id, triggerId, eventId, "stub-v1", NOW, NOW)).isOne();
        assertThat(repository.insertPendingIfAbsent(UUID.randomUUID(), triggerId, eventId, "v2", NOW, NOW)).isZero();

        var execution = repository.findById(id).orElseThrow();
        assertThat(execution.getStatus()).isEqualTo(RecallExecutionStatus.PENDING);
        assertThat(execution.getResult()).isNull();
        assertThat(execution.getProposalType()).isNull();
        assertThat(execution.getProposalPayload()).isNull();
        assertThat(execution.getAgentVersion()).isEqualTo("stub-v1");
        assertThat(repository.count()).isOne();
    }

    @Test
    void sameEventIdIsAllowedForDifferentTriggersAndLookupsStayScoped() {
        var trigger = triggerRepository.findById(triggerId).orElseThrow();
        UUID otherMemberId = memberRepository.saveAndFlush(Member.create(
                AuthProvider.KAKAO, UUID.randomUUID().toString(), NOW)).getId();
        UUID otherTriggerId = UUID.randomUUID();
        triggerRepository.insertIfAbsent(otherTriggerId, otherMemberId, trigger.getPlaceId(), NOW);
        UUID eventId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        assertThat(repository.insertPendingIfAbsent(firstId, triggerId, eventId, "stub-v1", NOW, NOW)).isOne();
        assertThat(repository.existsByTriggerIdAndTriggerEventId(triggerId, eventId)).isTrue();
        assertThat(repository.existsByTriggerIdAndTriggerEventId(otherTriggerId, eventId)).isFalse();
        assertThat(repository.findByTriggerIdAndTriggerEventId(otherTriggerId, eventId)).isEmpty();
        assertThat(repository.insertPendingIfAbsent(secondId, otherTriggerId, eventId, "stub-v1", NOW, NOW)).isOne();
        assertThat(repository.findByTriggerIdAndTriggerEventId(triggerId, eventId)).get()
                .extracting(execution -> execution.getId()).isEqualTo(firstId);
        assertThat(repository.findByTriggerIdAndTriggerEventId(otherTriggerId, eventId)).get()
                .extracting(execution -> execution.getId()).isEqualTo(secondId);
        assertThat(repository.insertPendingIfAbsent(UUID.randomUUID(), otherTriggerId, eventId, "v2", NOW, NOW)).isZero();
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void rejectsMissingTriggerForeignKey() {
        assertThatThrownBy(() -> repository.insertPendingIfAbsent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "stub-v1", NOW, NOW))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_recall_execution_trigger");
    }

    @Test
    void deletingTriggerCascadesToExecution() {
        UUID id = insertPending();
        jdbcTemplate.update("DELETE FROM trigger WHERE id = ?", triggerId);
        assertThat(repository.existsById(id)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "status, UNKNOWN, ck_recall_execution_status",
            "result, UNKNOWN, ck_recall_execution_result",
            "proposal_type, UNKNOWN, ck_recall_execution_proposal_type"
    })
    void rejectsValuesOutsideEnumChecks(String column, String value, String constraint) {
        UUID id = insertPending();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE recall_execution SET " + column + " = ? WHERE id = ?", value, id))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining(constraint);
    }

    @Test
    void proposalPayloadMapsToJsonb() {
        UUID id = insertPending();
        jdbcTemplate.update("""
                UPDATE recall_execution SET proposal_payload = '{"contentIds": ["sample"]}'::jsonb WHERE id = ?
                """, id);
        assertThat(repository.findById(id).orElseThrow().getProposalPayload())
                .containsEntry("contentIds", java.util.List.of("sample"));
    }

    private UUID insertPending() {
        UUID id = UUID.randomUUID();
        repository.insertPendingIfAbsent(id, triggerId, UUID.randomUUID(), "stub-v1", NOW, NOW);
        return id;
    }
}
